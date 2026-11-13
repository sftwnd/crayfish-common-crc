/* Crayfish Common CRC: the script of the landing page. The page is readable without it. */
(function () {
  'use strict';

  // ---- theme ----

  var root = document.documentElement;
  var toggle = document.getElementById('theme-toggle');
  if (toggle) {
    toggle.hidden = false;
    toggle.addEventListener('click', function () {
      var dark = root.getAttribute('data-theme') === 'dark'
          || (!root.hasAttribute('data-theme') && window.matchMedia('(prefers-color-scheme: dark)').matches);
      var theme = dark ? 'light' : 'dark';
      root.setAttribute('data-theme', theme);
      try {
        localStorage.setItem('theme', theme);
      } catch (e) { /* no storage: the theme lasts until the page is left */ }
    });
  }

  // ---- highlighting ----

  var JAVA = [
    ['c', /\/\/[^\n]*/y],
    ['s', /"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'/y],
    ['n', /\b(?:0x[0-9A-Fa-f_]+|\d[\d_]*)L?\b/y],
    ['k', /\b(?:boolean|byte|final|if|implementation|int|long|module|new|private|requires|return|static|try|while)\b/y],
    ['t', /\b[A-Z][A-Za-z0-9_]*\b/y]
  ];
  var XML = [
    ['k', /<\/?[\w.-]+>/y]
  ];
  var RULES = { java: JAVA, groovy: JAVA, kotlin: JAVA, xml: XML };
  var VALUE = /0x[0-9A-Fa-f]+/g;

  function span(kind, text) {
    var element = document.createElement('span');
    element.className = 'tok-' + kind;
    element.textContent = text;
    return element;
  }

  /** The comment as it is, but with the hexadecimal values in it marked: they are the results of the examples. */
  function comment(text) {
    var element = span('c', '');
    var last = 0;
    var match;
    VALUE.lastIndex = 0;
    while ((match = VALUE.exec(text)) !== null) {
      element.appendChild(document.createTextNode(text.slice(last, match.index)));
      element.appendChild(span('v', match[0]));
      last = match.index + match[0].length;
    }
    element.appendChild(document.createTextNode(text.slice(last)));
    return element;
  }

  function highlight(code) {
    var language = (/language-(\w+)/.exec(code.className) || [])[1];
    var rules = RULES[language];
    if (!rules) {
      return;
    }
    var text = code.textContent;
    var fragment = document.createDocumentFragment();
    var plain = '';
    var position = 0;
    while (position < text.length) {
      var found = null;
      for (var i = 0; i < rules.length && !found; i++) {
        var pattern = rules[i][1];
        pattern.lastIndex = position;
        var match = pattern.exec(text);
        if (match && match[0]) {
          found = [rules[i][0], match[0]];
        }
      }
      if (found) {
        if (plain) {
          fragment.appendChild(document.createTextNode(plain));
          plain = '';
        }
        fragment.appendChild(found[0] === 'c' ? comment(found[1]) : span(found[0], found[1]));
        position += found[1].length;
      } else {
        plain += text[position++];
      }
    }
    if (plain) {
      fragment.appendChild(document.createTextNode(plain));
    }
    code.textContent = '';
    code.appendChild(fragment);
  }

  function addCopy(pre) {
    if (!navigator.clipboard) {
      return;
    }
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'copy';
    button.textContent = 'Copy';
    button.addEventListener('click', function () {
      navigator.clipboard.writeText(pre.querySelector('code').textContent).then(function () {
        button.textContent = 'Copied';
        button.classList.add('done');
        setTimeout(function () {
          button.textContent = 'Copy';
          button.classList.remove('done');
        }, 1600);
      }, function () { /* no permission: nothing is copied */ });
    });
    pre.appendChild(button);
  }

  Array.prototype.forEach.call(document.querySelectorAll('pre > code'), function (code) {
    highlight(code);
    addCopy(code.parentNode);
  });

  // ---- tabs ----

  Array.prototype.forEach.call(document.querySelectorAll('[data-tabs]'), function (tabs) {
    var buttons = Array.prototype.slice.call(tabs.querySelectorAll('[role="tab"]'));

    function select(button, focus) {
      buttons.forEach(function (other) {
        var selected = other === button;
        other.setAttribute('aria-selected', String(selected));
        other.tabIndex = selected ? 0 : -1;
        document.getElementById(other.getAttribute('aria-controls')).hidden = !selected;
      });
      if (focus) {
        button.focus();
        if (button.scrollIntoView) {
          button.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        }
      }
    }

    buttons.forEach(function (button, index) {
      button.addEventListener('click', function () {
        select(button, false);
      });
      button.addEventListener('keydown', function (event) {
        var target = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: buttons.length - 1 }[event.key];
        if (target !== undefined) {
          event.preventDefault();
          select(buttons[(target + buttons.length) % buttons.length], true);
        }
      });
    });
    select(buttons[0], false);
  });

  // ---- the calculator and the catalogue ----

  var form = document.getElementById('calc');
  var chips = Array.prototype.slice.call(document.querySelectorAll('.alg'));
  if (!form || !chips.length || typeof BigInt !== 'function' || typeof TextEncoder !== 'function') {
    if (form) {
      form.hidden = true;
    }
    return;
  }

  var models = chips.map(function (chip) {
    var p = chip.getAttribute('data-params').split(' ');
    var aliases = chip.getAttribute('data-aliases');
    return {
      chip: chip,
      name: chip.textContent,
      aliases: aliases ? aliases.split(' ') : [],
      width: Number(p[0]),
      poly: p[1],
      init: p[2],
      refin: p[3] === '1',
      refout: p[4] === '1',
      xorout: p[5],
      check: p[6],
      residue: p[7]
    };
  });

  /** The CRC of the bytes, a bit at a time: the definition of the parameters of the catalogue. */
  function crc(model, bytes) {
    var width = BigInt(model.width);
    var mask = (1n << width) - 1n;
    var top = 1n << (width - 1n);
    var poly = BigInt('0x' + model.poly);
    var register = BigInt('0x' + model.init);
    for (var i = 0; i < bytes.length; i++) {
      for (var bit = 0; bit < 8; bit++) {
        var input = model.refin ? (bytes[i] >> bit) & 1 : (bytes[i] >> (7 - bit)) & 1;
        var feedback = ((register & top) !== 0n) !== (input === 1);
        register = (register << 1n) & mask;
        if (feedback) {
          register ^= poly;
        }
      }
    }
    if (model.refout) {
      var reflected = 0n;
      for (var k = 0n; k < width; k++) {
        if ((register >> k) & 1n) {
          reflected |= 1n << (width - 1n - k);
        }
      }
      register = reflected;
    }
    return register ^ BigInt('0x' + model.xorout);
  }

  function hex(value, width) {
    return value.toString(16).toUpperCase().padStart(Math.floor((width + 3) / 4), '0');
  }

  var input = document.getElementById('calc-input');
  var select = document.getElementById('calc-model');
  var hint = document.getElementById('calc-hint');
  var modeButtons = Array.prototype.slice.call(form.querySelectorAll('[data-mode]'));
  var mode = 'text';
  var current = null;

  // the algorithms of the select, grouped by the width as the catalogue is
  var group = null;
  models.forEach(function (model, index) {
    if (!group || group.getAttribute('data-width') !== String(model.width)) {
      group = document.createElement('optgroup');
      group.label = 'CRC-' + model.width;
      group.setAttribute('data-width', String(model.width));
      select.appendChild(group);
    }
    var option = document.createElement('option');
    option.value = String(index);
    option.textContent = model.name;
    group.appendChild(option);
  });

  /** The bytes of the data, or null with the hint of the mistake. */
  function bytes() {
    var text = input.value;
    if (mode === 'text') {
      hint.textContent = 'The bytes of the text in UTF-8';
      hint.classList.remove('error');
      return new TextEncoder().encode(text);
    }
    var digits = text.replace(/0x/gi, '').replace(/[\s,:;-]/g, '');
    if (/[^0-9a-f]/i.test(digits) || digits.length % 2 !== 0) {
      hint.textContent = /[^0-9a-f]/i.test(digits)
          ? 'Hexadecimal digits only: 31 32 33, 0x31 0x32 or 313233'
          : 'An even number of hexadecimal digits: two per byte';
      hint.classList.add('error');
      return null;
    }
    hint.textContent = 'Hexadecimal bytes: 31 32 33, 0x31 0x32 or 313233';
    hint.classList.remove('error');
    var result = new Uint8Array(digits.length / 2);
    for (var i = 0; i < result.length; i++) {
      result[i] = parseInt(digits.substr(2 * i, 2), 16);
    }
    return result;
  }

  function parameter(list, name, value) {
    var item = document.createElement('div');
    var term = document.createElement('dt');
    var description = document.createElement('dd');
    term.textContent = name;
    description.textContent = value;
    item.appendChild(term);
    item.appendChild(description);
    list.appendChild(item);
  }

  function update() {
    var model = current;
    var data = bytes();
    document.getElementById('calc-name').textContent = model.name;
    document.getElementById('calc-length').textContent = data === null ? 'the data'
        : data.length + (data.length === 1 ? ' byte' : ' bytes');
    document.getElementById('calc-value').textContent = data === null ? '—' : '0x' + hex(crc(model, data), model.width);
    document.getElementById('calc-aliases').textContent = model.aliases.length
        ? (model.aliases.length === 1 ? 'Alias: ' : 'Aliases: ') + model.aliases.join(', ') : '';
    var list = document.getElementById('calc-params');
    list.textContent = '';
    parameter(list, 'width', String(model.width));
    parameter(list, 'poly', '0x' + model.poly);
    parameter(list, 'init', '0x' + model.init);
    parameter(list, 'xorout', '0x' + model.xorout);
    parameter(list, 'refin', String(model.refin));
    parameter(list, 'refout', String(model.refout));
    parameter(list, 'check', '0x' + model.check);
    parameter(list, 'residue', '0x' + model.residue);
    var code = document.getElementById('calc-code');
    code.textContent = 'long crc = CrcModel.of("' + model.name + '")\n        .value(data);';
    highlight(code);
  }

  function choose(model) {
    if (current) {
      current.chip.setAttribute('aria-pressed', 'false');
    }
    current = model;
    model.chip.setAttribute('aria-pressed', 'true');
    select.value = String(models.indexOf(model));
    update();
  }

  select.addEventListener('change', function () {
    choose(models[Number(select.value)]);
  });
  input.addEventListener('input', update);
  form.addEventListener('submit', function (event) {
    event.preventDefault();
  });
  modeButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      var next = button.getAttribute('data-mode');
      if (next === mode) {
        return;
      }
      // the same bytes in the other format, when they have one
      var data = bytes();
      mode = next;
      modeButtons.forEach(function (other) {
        other.setAttribute('aria-pressed', String(other === button));
      });
      if (data !== null) {
        if (mode === 'hex') {
          input.value = Array.prototype.map.call(data, function (b) {
            return b.toString(16).padStart(2, '0');
          }).join(' ');
        } else {
          try {
            input.value = new TextDecoder('utf-8', { fatal: true }).decode(data);
          } catch (e) {
            input.value = '';
          }
        }
      }
      update();
    });
  });
  chips.forEach(function (chip, index) {
    chip.setAttribute('aria-pressed', 'false');
    chip.addEventListener('click', function () {
      choose(models[index]);
      var box = form.getBoundingClientRect();
      if (box.bottom < 80 || box.top > window.innerHeight - 120) {
        form.scrollIntoView({ block: 'start' });
      }
    });
  });

  // the filter of the catalogue

  var filter = document.getElementById('filter');
  var count = document.getElementById('filter-count');
  var sections = Array.prototype.slice.call(document.querySelectorAll('.catalogue .width'));
  var none = document.createElement('p');
  none.className = 'no-match';
  none.textContent = 'No algorithm of the catalogue has such a name or alias.';
  none.hidden = true;
  sections[sections.length - 1].parentNode.insertBefore(none, sections[sections.length - 1].nextSibling);

  filter.addEventListener('input', function () {
    var query = filter.value.trim().toUpperCase();
    var shown = 0;
    models.forEach(function (model) {
      var match = !query || model.name.indexOf(query) >= 0 || model.aliases.some(function (alias) {
        return alias.indexOf(query) >= 0;
      });
      model.chip.parentNode.hidden = !match;
      shown += match ? 1 : 0;
    });
    sections.forEach(function (section) {
      section.hidden = !section.querySelector('li:not([hidden])');
    });
    none.hidden = shown > 0;
    count.textContent = query ? shown + ' of ' + models.length : models.length + ' algorithms';
  });

  choose(models.filter(function (model) {
    return model.name === 'CRC-16/MODBUS';
  })[0] || models[0]);
})();
