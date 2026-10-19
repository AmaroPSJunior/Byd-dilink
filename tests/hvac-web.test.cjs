const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

function page(response, state={}) {
  const elements=new Map();
  const document={getElementById(id){if(!elements.has(id))elements.set(id,{value:'1',textContent:'',className:''});return elements.get(id)}};
  const requests=[];
  const context=vm.createContext({document,URLSearchParams,location:{search:'',pathname:'/'},
    localStorage:{getItem(){return 'test-only'},setItem(){}},history:{replaceState(){}},setInterval(){},
    fetch:async(path,options)=>{requests.push({path,options});const data=path==='/api/state'?{ok:true,sunshade:{},...state}:response;
      return {ok:true,status:200,text:async()=>JSON.stringify(data)}}});
  const html=fs.readFileSync('app/src/main/assets/web/index.html','utf8');
  const script=html.match(/<script>([\s\S]*?)<\/script>/)[1].replace('createWindows();refresh();setInterval(refresh,2500);','');
  vm.runInContext(script,context);
  return {context,elements,requests};
}

test('legacy HTTP 200 acceptance without confirmation is not a successful power command',async()=>{
  const p=page({ok:true,accepted:true,message:'solicitação aceita'});
  assert.equal(await vm.runInContext("command('/api/climate/power',{on:true})",p.context),false);
  assert.match(p.elements.get('climateStatus').className,/err/);
  assert.equal(p.requests.filter(r=>r.options.method==='POST').length,1);
});

test('explicit backend confirmation is displayed as success',async()=>{
  const p=page({ok:true,accepted:true,confirmed:true,message:'estado confirmado'});
  assert.equal(await vm.runInContext("command('/api/climate/power',{on:false})",p.context),true);
  assert.match(p.elements.get('climateStatus').className,/ok/);
});

test('manual refusal does not send another command or suggest changing security settings',async()=>{
  const p=page({ok:false,accepted:false,confirmed:false,manualInteractionRequired:true,message:'Use o painel original.'});
  assert.equal(await vm.runInContext("command('/api/climate/power',{on:true})",p.context),false);
  assert.equal(p.requests.length,1);
  await vm.runInContext('refresh()',p.context);
  assert.match(p.elements.get('hvacAccessHint').textContent,/painel original/);
  assert.doesNotMatch(p.elements.get('hvacAccessHint').textContent,/habilite|Configurações/);
});

test('raw power state never becomes a claimed ON/OFF confirmation',async()=>{
  const p=page({}, {climate:{powerState:1},hvacAccessibilityEnabled:false});
  await vm.runInContext('refresh()',p.context);
  assert.match(p.elements.get('powerState').textContent,/valor bruto.*1/);
  assert.match(p.elements.get('powerState').textContent,/não confirma/);
});
