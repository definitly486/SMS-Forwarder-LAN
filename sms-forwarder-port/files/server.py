#!/usr/bin/env python3
"""
SMS Forwarder LAN — сервер для приёма SMS с Android.
HTTP (8080) + HTTPS (8443) + простой веб-интерфейс.
"""


import sys
import os
import json
import ssl
import threading
from functools import wraps

from flask import Flask, request, jsonify, render_template_string
from flask_cors import CORS

from database import Database

with open("config.json") as f:
    cfg = json.load(f)

db = Database(cfg["db_path"])
app = Flask(__name__)
CORS(app)

TOKEN = cfg["token"]


def require_token(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or auth[7:] != TOKEN:
            return jsonify({"error": "unauthorized"}), 401
        return f(*args, **kwargs)
    return decorated


@app.route("/api/sms", methods=["POST"])
@require_token
def receive_sms():
    data = request.get_json(force=True)
    sender = data.get("sender", "")
    body = data.get("body", "")
    is_code = bool(data.get("is_code", False))
    if not body:
        return jsonify({"error": "empty body"}), 400
    db.add_message(sender, body, is_code)
    return jsonify({"status": "ok"})


@app.route("/api/messages")
@require_token
def list_messages():
    sender = request.args.get("sender")
    search = request.args.get("q")
    limit = int(request.args.get("limit", 100))
    return jsonify(db.get_messages(limit=limit, sender=sender, search=search))


@app.route("/")
def index():
    html = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>SMS Forwarder</title>
        <style>
            body { font-family: system-ui, -apple-system, sans-serif; max-width: 960px; margin: 2rem auto; padding: 0 1rem; background: #f8f9fa; }
            h1 { color: #212529; }
            .controls { margin-bottom: 1rem; display: flex; flex-wrap: wrap; gap: 8px; }
            input, button { padding: 8px 12px; border: 1px solid #ced4da; border-radius: 6px; font-size: 14px; }
            button { background: #0d6efd; color: white; border: none; cursor: pointer; }
            button:hover { background: #0b5ed7; }
            table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
            th, td { border-bottom: 1px solid #eee; padding: 10px 12px; text-align: left; vertical-align: top; }
            th { background: #e9ecef; font-weight: 600; }
            tr.code { background: #fff3cd; }
            .meta { color: #6c757d; font-size: 12px; }
            #status { margin-left: 8px; color: #6c757d; }
        </style>
    </head>
    <body>
        <h1>SMS Forwarder LAN</h1>
        <div class="controls">
            <input id="token" type="password" placeholder="Bearer token" style="min-width:260px">
            <input id="search" placeholder="Поиск по тексту..." style="min-width:180px">
            <button onclick="load()">Обновить</button>
            <span id="status"></span>
        </div>
        <table>
            <thead>
                <tr><th>Время</th><th>От</th><th>Текст</th><th>Код?</th></tr>
            </thead>
            <tbody id="list"></tbody>
        </table>
        <script>
            const tokenInput = document.getElementById('token');
            tokenInput.value = localStorage.getItem('sms_forwarder_token') || '';
            tokenInput.addEventListener('input', () => {
                localStorage.setItem('sms_forwarder_token', tokenInput.value.trim());
            });

            async function load() {
                const token = tokenInput.value.trim();
                const q = document.getElementById('search').value;
                const status = document.getElementById('status');
                status.textContent = 'загрузка...';
                try {
                    const r = await fetch('/api/messages?q=' + encodeURIComponent(q) + '&limit=200', {
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                    if (!r.ok) {
                        if (r.status === 401) {
                            status.textContent = '401: введите тот же токен, что указан в config.json на сервере и в Android';
                        } else {
                            status.textContent = 'ошибка ' + r.status;
                        }
                        return;
                    }
                    const data = await r.json();
                    status.textContent = data.length + ' сообщений';
                    const tbody = document.getElementById('list');
                    tbody.innerHTML = data.map(m => `
                        <tr class="${m.is_code ? 'code' : ''}">
                            <td class="meta">${m.received_at}</td>
                            <td>${escapeHtml(m.sender)}</td>
                            <td>${escapeHtml(m.body)}</td>
                            <td>${m.is_code ? 'да' : ''}</td>
                        </tr>
                    `).join('');
                } catch (e) {
                    status.textContent = 'сеть недоступна';
                }
            }
            function escapeHtml(s) {
                return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
            }
            // Автозагрузка при Enter в поле поиска
            document.getElementById('search').addEventListener('keydown', e => {
                if (e.key === 'Enter') load();
            });
            if (tokenInput.value) load();
        </script>
    </body>
    </html>
    """
    return render_template_string(html)


if __name__ == "__main__":
    import os

    # Логи в файл, чтобы видеть ошибки под daemon
    log_path = os.path.join(os.path.dirname(__file__) or ".", "server.log")
    try:
        log_f = open(log_path, "a", buffering=1)
        sys.stdout = log_f
        sys.stderr = log_f
    except Exception:
        pass

    import sys
    from datetime import datetime
    print(f"\n=== start {datetime.utcnow().isoformat()}Z ===", flush=True)

    cert = cfg.get("cert_file", "cert.pem")
    key = cfg.get("key_file", "key.pem")

    # HTTPS, если есть сертификаты
    if os.path.isfile(cert) and os.path.isfile(key):
        try:
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.load_cert_chain(cert, key)
            print(f"HTTPS on {cfg['host']}:{cfg['https_port']}", flush=True)
            app.run(
                host=cfg["host"],
                port=cfg["https_port"],
                ssl_context=context,
                debug=False,
                use_reloader=False,
                threaded=True,
            )
            sys.exit(0)
        except Exception as e:
            print(f"HTTPS failed: {e}", flush=True)

    # HTTP (основной режим без сертификатов)
    print(f"HTTP on {cfg['host']}:{cfg['http_port']}", flush=True)
    app.run(
        host=cfg["host"],
        port=cfg["http_port"],
        debug=False,
        use_reloader=False,
        threaded=True,
    )