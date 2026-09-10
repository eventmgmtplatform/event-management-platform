import {useEffect,useState} from 'react';
import {EssError,readEss} from './ess.adapter';
// The path/refresh key gates rendering as well as effects: old tenant/event data
// can never be rendered while the new request is waiting for its effect to run.
export function useEssRequest<T>(path:string,parse:(value:unknown)=>T,refresh=0){
 const key=path+'#'+refresh;
 const [result,setResult]=useState<{key:string;data?:T;error?:EssError}>();
 useEffect(()=>{const controller=new AbortController();let active=true;const timer=setTimeout(()=>controller.abort(),12000);
  readEss(path,parse,controller.signal).then(data=>{if(active)setResult({key,data});}).catch(error=>{if(active)setResult({key,error:error instanceof EssError?error:new EssError(503,'ESS_UNAVAILABLE')});}).finally(()=>clearTimeout(timer));
  return()=>{active=false;clearTimeout(timer);controller.abort();};
 },[key,path,parse]);
 return result?.key===key?{...result,loading:false}:{loading:true,data:undefined,error:undefined};
}
