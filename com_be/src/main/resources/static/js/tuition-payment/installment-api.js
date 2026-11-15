const installmentApi = (() => {
 const staff='/api/tuition-payment';
 const parent='/api/parent-tuition';
 const call=(url,method='GET',data=null)=>callApi(url,method,data,false,false);
 return {
  invoices: isParent=>call(isParent?parent+'/invoices':staff+'/receivables'),
  detail: (id,isParent)=>call(isParent?parent+'/invoices/'+id:staff+'/'+id+'/plan'),
  plan: (id,data)=>call(staff+'/'+id+'/plan','POST',data),
  reservation: (id,data)=>call(staff+'/'+id+'/reservation','POST',data),
  collect: (id,data)=>call(staff+'/'+id+'/collections','POST',data),
  discount: (id,data)=>call(staff+'/'+id+'/apply-discount','POST',data),
  intent: (id,amount,isParent)=>call((isParent?parent+'/invoices/':staff+'/')+id+'/payment-intents','POST',{amount}),
  intentStatus: (id,isParent)=>call((isParent?parent:staff)+'/payment-intents/'+id),
  cancelIntent: (id,isParent)=>call((isParent?parent:staff)+'/payment-intents/'+id+'/cancel','POST'),
  confirmBank: (id,data)=>call(staff+'/payment-intents/'+id+'/confirm-bank','POST',data),
  simulate: id=>call(staff+'/payment-intents/'+id+'/simulate','POST'),
  notices: ()=>call(parent+'/notifications'),
  readNotice: id=>call(parent+'/notifications/'+id+'/read','PATCH'),
  reversals: ()=>call(staff+'/reversals'),
  reversal: (id,reason)=>call(staff+'/payments/'+id+'/reversal','POST',{reason}),
  reviewReversal: (id,approve,note)=>call(staff+'/reversals/'+id+'/review','POST',{approve,note}),
 };
})();
