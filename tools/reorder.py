#!/usr/bin/env python3
# drag and drop editor for the button order in remotes.json: drop on a button to swap the two. stdlib only, no tkinter on this box.
# usage: tools/reorder.py  then open http://localhost:8765
import json, webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

JSON = Path(__file__).resolve().parent.parent / "app/src/main/assets/remotes.json"
PORT = 8765

PAGE = """<!doctype html><meta charset=utf-8><title>remotes.json</title>
<style>
body{background:#000;color:#fff;font:14px sans-serif;margin:16px}
nav button{margin-right:8px;padding:6px 12px} nav button.on{outline:2px solid #fff}
#grid{display:grid;gap:8px;margin:16px 0;max-width:480px}
#grid div{height:56px;border-radius:12px;display:flex;align-items:center;justify-content:center;text-align:center;cursor:grab;user-select:none}
#grid div.over{outline:3px dashed #fff}
#save{padding:8px 24px}
</style>
<nav id=tabs></nav><div id=grid></div><button id=save>save</button> <span id=msg></span>
<script>
const C={red:'#C62828',green:'#2E7D32',blue:'#1565C0',yellow:'#F9A825'};
let data,cur=0,from=null;
const grid=document.getElementById('grid'),tabs=document.getElementById('tabs');
function render(){
  tabs.innerHTML='';data.forEach((r,i)=>{const b=document.createElement('button');b.textContent=r.name;b.className=i==cur?'on':'';b.onclick=()=>{cur=i;render()};tabs.append(b)});
  const r=data[cur];grid.style.gridTemplateColumns=`repeat(${r.columns},1fr)`;grid.innerHTML='';
  r.buttons.forEach((b,i)=>{
    const d=document.createElement('div');d.textContent=b.label;d.draggable=true;
    d.style.background=C[b.color]||'#303030';d.style.color=b.color=='yellow'?'#000':'#fff';
    d.ondragstart=()=>from=i;
    d.ondragover=e=>{e.preventDefault();d.classList.add('over')};
    d.ondragleave=()=>d.classList.remove('over');
    d.ondrop=e=>{e.preventDefault();if(from===null||from===i)return;[r.buttons[from],r.buttons[i]]=[r.buttons[i],r.buttons[from]];from=null;render()};
    grid.append(d)});
}
fetch('/data').then(r=>r.json()).then(d=>{data=d;render()});
document.getElementById('save').onclick=()=>fetch('/data',{method:'POST',body:JSON.stringify(data)}).then(r=>document.getElementById('msg').textContent=r.ok?'saved':'error');
</script>"""

class H(BaseHTTPRequestHandler):
    def _send(self, body, ctype):
        self.send_response(200); self.send_header("Content-Type", ctype); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        if self.path == "/data": self._send(JSON.read_bytes(), "application/json")
        else: self._send(PAGE.encode(), "text/html")
    def do_POST(self):
        data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        JSON.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n")
        self._send(b"ok", "text/plain")
    def log_message(self, *a): pass

if __name__ == "__main__":
    print(f"editing {JSON}\nhttp://localhost:{PORT}  (ctrl-c to stop)")
    webbrowser.open(f"http://localhost:{PORT}")
    HTTPServer(("127.0.0.1", PORT), H).serve_forever()
