/*
 * VidSaver DOM media sniffer.
 *
 * Injected after page load and re-run on DOM mutation. It only reads what the
 * page has already put in the document -- <video>/<source> elements and the
 * og:video/twitter:player meta tags the page publishes for link previews. It
 * does not call any site API, read credentials, or touch storage.
 *
 * Findings are posted to the VidSaverBridge JavascriptInterface as JSON.
 */
(function () {
  'use strict';

  if (window.__vidSaverSnifferInstalled) {
    // Re-injection (e.g. after a same-page navigation) just triggers a rescan.
    window.__vidSaverScan();
    return;
  }
  window.__vidSaverSnifferInstalled = true;

  var BRIDGE = window.VidSaverBridge;
  if (!BRIDGE || typeof BRIDGE.onMediaFound !== 'function') {
    return;
  }

  var DEBOUNCE_MS = 400;
  var MAX_REPORTS = 50;
  var lastPayload = '';
  var scanTimer = null;

  function isUsableUrl(url) {
    if (!url || typeof url !== 'string') return false;
    // blob:/data: URLs exist only inside the page and cannot be re-fetched.
    return url.indexOf('blob:') !== 0 && url.indexOf('data:') !== 0;
  }

  function pageTitle() {
    var og = document.querySelector('meta[property="og:title"]');
    if (og && og.content) return og.content;
    return document.title || null;
  }

  function addCandidate(out, url, extra) {
    if (!isUsableUrl(url)) return;
    for (var i = 0; i < out.length; i++) {
      if (out[i].url === url) return;
    }
    if (out.length >= MAX_REPORTS) return;
    var entry = { url: url };
    if (extra) {
      if (extra.mime) entry.mime = extra.mime;
      if (extra.poster) entry.poster = extra.poster;
      if (extra.width) entry.width = extra.width;
      if (extra.height) entry.height = extra.height;
      if (extra.duration) entry.duration = extra.duration;
      if (extra.title) entry.title = extra.title;
    }
    out.push(entry);
  }

  function collectVideoElements(out) {
    var videos = document.getElementsByTagName('video');
    for (var i = 0; i < videos.length; i++) {
      var video = videos[i];
      var shared = {
        poster: video.getAttribute('poster') || null,
        width: video.videoWidth || null,
        height: video.videoHeight || null,
        duration: isFinite(video.duration) ? video.duration : null,
        title: video.getAttribute('title') || null
      };

      // currentSrc is what the element actually resolved to; src is what the
      // page asked for. Both are worth reporting.
      addCandidate(out, video.currentSrc, shared);
      addCandidate(out, video.getAttribute('src'), shared);

      var sources = video.getElementsByTagName('source');
      for (var j = 0; j < sources.length; j++) {
        addCandidate(out, sources[j].getAttribute('src'), {
          mime: sources[j].getAttribute('type') || null,
          poster: shared.poster,
          width: shared.width,
          height: shared.height,
          duration: shared.duration,
          title: shared.title
        });
      }
    }
  }

  function collectMetaTags(out) {
    var selectors = [
      'meta[property="og:video"]',
      'meta[property="og:video:url"]',
      'meta[property="og:video:secure_url"]',
      'meta[name="twitter:player:stream"]'
    ];
    var poster = document.querySelector('meta[property="og:image"]');
    for (var i = 0; i < selectors.length; i++) {
      var nodes = document.querySelectorAll(selectors[i]);
      for (var j = 0; j < nodes.length; j++) {
        var typeNode = document.querySelector('meta[property="og:video:type"]');
        addCandidate(out, nodes[j].getAttribute('content'), {
          mime: typeNode ? typeNode.getAttribute('content') : null,
          poster: poster ? poster.getAttribute('content') : null,
          title: pageTitle()
        });
      }
    }
  }

  function scan() {
    var found = [];
    try {
      collectVideoElements(found);
      collectMetaTags(found);
    } catch (e) {
      // A page that breaks our scan must not break the page.
      return;
    }
    if (!found.length) return;

    var payload = JSON.stringify({
      pageUrl: window.location.href,
      title: pageTitle(),
      media: found
    });

    // Only post when something actually changed.
    if (payload === lastPayload) return;
    lastPayload = payload;

    try {
      BRIDGE.onMediaFound(payload);
    } catch (e) {
      /* bridge went away; nothing to do */
    }
  }

  function scheduleScan() {
    if (scanTimer) clearTimeout(scanTimer);
    scanTimer = setTimeout(scan, DEBOUNCE_MS);
  }

  window.__vidSaverScan = scheduleScan;

  // Re-scan as the page changes: new elements, src swaps, and the moment a
  // video actually starts resolving a source.
  try {
    var observer = new MutationObserver(scheduleScan);
    observer.observe(document.documentElement || document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['src', 'currentSrc', 'poster', 'content']
    });
  } catch (e) {
    /* MutationObserver unavailable; the event hooks below still fire */
  }

  ['loadedmetadata', 'loadeddata', 'playing', 'durationchange'].forEach(function (event) {
    document.addEventListener(event, scheduleScan, true);
  });

  scan();
})();
