(() => {
  const statusEl = document.getElementById('status');
  const urlEl = document.getElementById('wsUrl');
  const connectBtn = document.getElementById('connectBtn');
  const turboBtn = document.getElementById('turbo');
  let socket = null, turbo = false, turboTimer = null;
  const active = new Set();
  function status(online, label) {
    statusEl.textContent = label || (online ? 'متصل' : 'غير متصل');
    statusEl.className = 'status ' + (online ? 'online' : 'offline');
    connectBtn.textContent = online ? 'فصل' : 'اتصال';
  }
  function send(data) {
    if (socket && socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(data));
  }
  function press(button, pressed) {
    const key = button.dataset.button;
    if (!key) return;
    if (pressed) active.add(key); else active.delete(key);
    button.classList.toggle('pressed', pressed);
    send({type:'button', button:key, pressed});
  }
  function bindButton(button) {
    button.addEventListener('pointerdown', e => { e.preventDefault(); button.setPointerCapture?.(e.pointerId); press(button,true); });
    const release = e => { e.preventDefault(); press(button,false); };
    button.addEventListener('pointerup', release);
    button.addEventListener('pointercancel', release);
    button.addEventListener('lostpointercapture', () => press(button,false));
  }
  document.querySelectorAll('[data-button]').forEach(bindButton);
  function bindStick(el) {
    const knob = el.querySelector('.knob'); let pointer = null;
    const move = e => {
      const r = el.getBoundingClientRect(), cx=r.left+r.width/2, cy=r.top+r.height/2;
      let x=(e.clientX-cx)/(r.width*.34), y=(e.clientY-cy)/(r.height*.34);
      const mag=Math.hypot(x,y); if(mag>1){x/=mag;y/=mag;}
      knob.style.transform = `translate(${x*27}px,${y*27}px)`;
      send({type:'stick',stick:el.dataset.stick,x:Number(x.toFixed(3)),y:Number((-y).toFixed(3))});
    };
    el.addEventListener('pointerdown',e=>{pointer=e.pointerId;el.setPointerCapture(pointer);move(e);});
    el.addEventListener('pointermove',e=>{if(pointer===e.pointerId)move(e);});
    const end=e=>{if(pointer!==e.pointerId)return;pointer=null;knob.style.transform='translate(0,0)';send({type:'stick',stick:el.dataset.stick,x:0,y:0});};
    el.addEventListener('pointerup',end);el.addEventListener('pointercancel',end);
  }
  document.querySelectorAll('[data-stick]').forEach(bindStick);
  connectBtn.addEventListener('click',()=>{
    if(socket && socket.readyState===WebSocket.OPEN){socket.close();return;}
    try{
      socket=new WebSocket(urlEl.value.trim());
      status(false,'جارٍ الاتصال…');
      socket.addEventListener('open',()=>status(true));
      socket.addEventListener('close',()=>{status(false);clearInterval(turboTimer);});
      socket.addEventListener('error',()=>status(false,'فشل الاتصال'));
    }catch(e){status(false,'عنوان غير صالح');}
  });
  turboBtn.addEventListener('click',()=>{
    turbo=!turbo;turboBtn.classList.toggle('active',turbo);turboBtn.textContent='TURBO: '+(turbo?'ON':'OFF');
    clearInterval(turboTimer);
    if(turbo) turboTimer=setInterval(()=>{if(active.has('cross')){send({type:'button',button:'cross',pressed:false});send({type:'button',button:'cross',pressed:true});}},90);
  });
  window.addEventListener('blur',()=>{document.querySelectorAll('[data-button]').forEach(b=>{if(active.has(b.dataset.button))press(b,false);});});
  status(false);
})();