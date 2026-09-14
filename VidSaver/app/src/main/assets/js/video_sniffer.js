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
        ' doc=' + document.documentElement.clientWidth + 'x' + document.documentElement.clientHeight +
        ' body=' + (document.body ? document.body.clientHeight : -1) +
        ' screen=' + window.screen.width + 'x' + window.screen.height + '/' + window.screen.availHeight +
        ' outer=' + window.outerHeight + ' dpr=' + window.devicePixelRatio +
        (vp ? ' scale=' + (Math.round(vp.scale * 100) / 100) : '') +
        ' disp=' + cs.display + ' op=' + cs.opacity + ' visb=' + cs.visibility +
        ' pos=' + cs.position + ' z=' + cs.zIndex;
    } catch (e) {
      return 'box=?';
    }
  }

  // The parent chain with each box's height and sizing style, so a player
  // whose height collapsed can be traced to the ancestor that collapsed it.
  function ancestorsOf(el) {
    var out = [];
    try {
      var node = el.parentElement;
      for (var depth = 0; node && depth < 8; depth++) {
        var r = node.getBoundingClientRect();
        var cs = window.getComputedStyle(node);
        var cls = (typeof node.className === 'string' ? node.className : '').split(/\s+/)[0] || '';
        out.push(node.tagName.toLowerCase() + (cls ? '.' + cls.substring(0, 24) : '') +
          ' ' + Math.round(r.width) + 'x' + Math.round(r.height) +
          ' h=' + cs.height + ' pos=' + cs.position + ' disp=' + cs.display);
        node = node.parentElement;
      }
    } catch (e) {
      out.push('?');
    }
    return out.join(' < ');
  }

  // The children of the highest zero-height ancestor whose own parent has
  // height: whichever in-flow child was supposed to give it height is here,
  // and if that child is an image its load state says whether it ever arrived.
  function collapsedChildren(el) {
    try {
      var node = el.parentElement;
      var top = null;
      while (node && node.getBoundingClientRect().height === 0) {
        top = node;
        node = node.parentElement;
      }
      if (!top) return '';
      var out = [];
      var kids = top.children;
      for (var i = 0; i < kids.length && i < 10; i++) {
        var k = kids[i];
        var r = k.getBoundingClientRect();
        var cs = window.getComputedStyle(k);
        var cls = (typeof k.className === 'string' ? k.className : '').split(/\s+/)[0] || '';
        var line = k.tagName.toLowerCase() + (cls ? '.' + cls.substring(0, 24) : '') +
          ' ' + Math.round(r.width) + 'x' + Math.round(r.height) +
          ' h=' + cs.height + ' pos=' + cs.position + ' disp=' + cs.display;
        if (k.tagName === 'IMG') {
          line += ' img:complete=' + k.complete + ' natural=' + k.naturalWidth + 'x' + k.naturalHeight +
            ' src=' + srcScheme(k.currentSrc || k.getAttribute('src')) +
            ':' + ((k.currentSrc || k.getAttribute('src') || '').split('/')[2] || '');
        }
        if (k.tagName === 'CANVAS') line += ' canvas=' + k.width + 'x' + k.height;
        out.push(line);
      }
      return out.join(' | ');
    } catch (e) {
      return '?';
    }
  }

  // A player that measured itself before it had a size keeps that size until
  // something tells it to look again. A resize event is how a browser tells it.
  var nudged = [];
  function nudgeIfCollapsed(video) {
    try {
      var r = video.getBoundingClientRect();
      if (r.height > 0 || video.readyState < 1) return;
      if (nudged.indexOf(video) >= 0) return;
      nudged.push(video);
      window.dispatchEvent(new Event('resize'));
      if (window.visualViewport) {
        try { window.visualViewport.dispatchEvent(new Event('resize')); } catch (e) { /* not dispatchable */ }
      }
      scheduleScan();
    } catch (e) {
      /* nothing to do */
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
          ' vis=' + visibleFraction(video) + ' ' + boxOf(video) +
          ' style="' + (video.getAttribute('style') || '').substring(0, 80) + '"' +
          ' up=[' + ancestorsOf(video) + ']' +
          ' kids=[' + collapsedChildren(video) + ']');
        nudgeIfCollapsed(video);
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

  // Measures what the page's layout can actually see: viewport units and
  // percentage heights, resolved live in this document. A player sized from
  // a unit that resolves to zero here explains a zero-height player.
  function unitsProbe() {
    try {
      var probe = document.createElement('div');
      probe.style.cssText = 'position:absolute;left:0;top:0;width:0;visibility:hidden;pointer-events:none;';
      var host = document.body || document.documentElement;
      host.appendChild(probe);
      var out = [];
      var units = ['100vh', '100dvh', '100svh', '100lvh', '100%', '-webkit-fill-available'];
      for (var i = 0; i < units.length; i++) {
        probe.style.height = units[i];
        out.push(units[i] + '=' + Math.round(probe.getBoundingClientRect().height));
      }
      host.removeChild(probe);
      var html = document.documentElement;
      var hcs = window.getComputedStyle(html);
      out.push('html=' + Math.round(html.getBoundingClientRect().height) + '(' + hcs.height + '/' + hcs.minHeight + ')');
      if (document.body) {
        var bcs = window.getComputedStyle(document.body);
        out.push('body=' + Math.round(document.body.getBoundingClientRect().height) + '(' + bcs.height + '/' + bcs.minHeight + ')');
      }
      return 'page units ' + out.join(' ');
    } catch (e) {
      return 'page units ?';
    }
  }

  // The caption of the clip that is playing. A feed page's <title> is the
  // site's slogan, so the only name worth saving under is the post's own
  // text, found near the active <video>. Site hooks first, then anything
  // that calls itself a description or caption, nearest ancestor first.
  var CAPTION_SELECTORS = [
    '[data-e2e="browse-video-desc"]', '[data-e2e="video-desc"]', '[data-e2e="new-desc-span"]',
    '[data-e2e*="desc"]', '[class*="video-meta-caption"]', '[class*="Caption"]', '[class*="caption"]',
    '[class*="Desc"]', '[class*="desc"]', 'figcaption', 'h1'
  ];

  function captionFor(video) {
    if (!video) return null;
    try {
      var node = video.parentElement;
      for (var depth = 0; node && depth < 8; depth++) {
        for (var i = 0; i < CAPTION_SELECTORS.length; i++) {
          var hits = node.querySelectorAll(CAPTION_SELECTORS[i]);
          for (var j = 0; j < hits.length && j < 5; j++) {
            var text = (hits[j].textContent || '').replace(/\s+/g, ' ').trim();
            if (text.length >= 3 && text.length <= 300) return text.substring(0, 160);
          }
        }
        node = node.parentElement;
      }
    } catch (e) {
      /* a page that breaks our lookup must not break the page */
    }
    return null;
  }

  function scan() {
    var found = [];
    var debug = [];
    try {
      collectVideoElements(found, debug);
      if (debug.length) debug.unshift(unitsProbe());
      collectMetaTags(found);
    } catch (e) {
      // A page that breaks our scan must not break the page.
      return;
    }
    if (!found.length && !debug.length) return;

    var payload = JSON.stringify({
      pageUrl: window.location.href,
      title: pageTitle(),
      caption: captionFor(lastActive),
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
