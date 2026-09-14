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

  // The <video> that most recently started or advanced playback. In a feed
  // every clip on the page is a <video>, most of them preloaded and silent;
  // the one the person is actually watching is the one that plays.
  var lastActive = null;

  function trackActive(event) {
    var target = event.target;
    if (target && target.tagName === 'VIDEO') {
      if (lastActive !== target) {
        lastActive = target;
        scheduleScan();
      }
    }
  }

  function srcScheme(url) {
    if (!url) return 'none';
    var i = url.indexOf(':');
    return i > 0 ? url.substring(0, i) : '?';
  }

  // Where the element is and how it is styled: enough to tell "zero-sized"
  // from "off screen" from "opacity 0" when a playing video is not visible.
  function boxOf(el) {
    try {
      var r = el.getBoundingClientRect();
      var cs = window.getComputedStyle(el);
      var vp = window.visualViewport;
      return 'rect=' + Math.round(r.width) + 'x' + Math.round(r.height) +
        '@' + Math.round(r.left) + ',' + Math.round(r.top) +
        ' vp=' + window.innerWidth + 'x' + window.innerHeight +
        (vp ? ' scale=' + (Math.round(vp.scale * 100) / 100) : '') +
        ' disp=' + cs.display + ' op=' + cs.opacity + ' visb=' + cs.visibility +
        ' pos=' + cs.position + ' z=' + cs.zIndex;
    } catch (e) {
      return 'box=?';
    }
  }

  // How much of the element is inside the viewport, 0..1.
  function visibleFraction(el) {
    try {
      var r = el.getBoundingClientRect();
      var area = r.width * r.height;
      if (!area) return 0;
      var vw = window.innerWidth || document.documentElement.clientWidth;
      var vh = window.innerHeight || document.documentElement.clientHeight;
      var w = Math.min(r.right, vw) - Math.max(r.left, 0);
      var h = Math.min(r.bottom, vh) - Math.max(r.top, 0);
      if (w <= 0 || h <= 0) return 0;
      return Math.round((w * h / area) * 100) / 100;
    } catch (e) {
      return 0;
    }
  }

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
      if (extra.playing) entry.playing = true;
      if (extra.active) entry.active = true;
      if (typeof extra.visible === 'number') entry.visible = extra.visible;
      if (extra.state) entry.state = extra.state;
    }
    out.push(entry);
  }

  function collectVideoElements(out, debug) {
    var videos = document.getElementsByTagName('video');
    for (var i = 0; i < videos.length; i++) {
      var video = videos[i];
      if (debug.length < 20) {
        // Every element, blob-sourced ones included: a page whose videos all
        // live in blob: URLs still has to be explainable from the log.
        debug.push(srcScheme(video.currentSrc || video.getAttribute('src')) +
          (video === lastActive ? ' active' : '') +
          (!video.paused && !video.ended ? ' playing' : ' paused') +
          ' rs=' + video.readyState + ' ns=' + video.networkState +
          ' err=' + (video.error ? video.error.code : 0) +
          ' ' + (video.videoWidth || 0) + 'x' + (video.videoHeight || 0) +
          ' vis=' + visibleFraction(video) + ' ' + boxOf(video));
      }
      var shared = {
        poster: video.getAttribute('poster') || null,
        width: video.videoWidth || null,
        height: video.videoHeight || null,
        duration: isFinite(video.duration) ? video.duration : null,
        title: video.getAttribute('title') || null,
        playing: !video.paused && !video.ended && video.readyState > 2,
        active: video === lastActive,
        visible: visibleFraction(video),
        // Diagnostic state, so a log can say why a player is a grey box:
        // readyState 0 = nothing loaded, networkState 3 = no usable source,
        // error 4 = format not supported.
        state: 'rs=' + video.readyState + ' ns=' + video.networkState +
          ' err=' + (video.error ? video.error.code : 0) +
          ' t=' + (Math.floor((video.currentTime || 0) / 10) * 10) +
          ' ' + (video.videoWidth || 0) + 'x' + (video.videoHeight || 0) +
          ' src=' + srcScheme(video.currentSrc || video.getAttribute('src'))
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
          title: shared.title,
          playing: shared.playing,
          active: shared.active,
          visible: shared.visible,
          state: shared.state
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
    var debug = [];
    try {
      collectVideoElements(found, debug);
      collectMetaTags(found);
    } catch (e) {
      // A page that breaks our scan must not break the page.
      return;
    }
    if (!found.length && !debug.length) return;

    var payload = JSON.stringify({
      pageUrl: window.location.href,
      title: pageTitle(),
      media: found,
      debug: debug
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

  ['loadedmetadata', 'loadeddata', 'playing', 'durationchange', 'pause', 'ended'].forEach(function (event) {
    document.addEventListener(event, scheduleScan, true);
  });
  // Capture phase: play events do not bubble, but capture still sees them.
  ['play', 'playing', 'timeupdate'].forEach(function (event) {
    document.addEventListener(event, trackActive, true);
  });
  // Scrolling a feed changes which clip is on screen without touching the DOM.
  window.addEventListener('scroll', scheduleScan, true);

  scan();
})();
