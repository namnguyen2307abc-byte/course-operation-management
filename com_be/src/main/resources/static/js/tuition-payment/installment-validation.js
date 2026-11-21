(function (root) {
 'use strict';
 const money = n => new Intl.NumberFormat('vi-VN').format(n) + 'đ';
 const date = d => d.split('-').reverse().join('/');
 const shift=(value,days)=>{const d=new Date(value+'T00:00:00Z');d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10);};
 function latest(end,count,index){return end?shift(end,-(count-index-1)):'';}
 const validDate=value=>/^\d{4}-\d{2}-\d{2}$/.test(value||'')&&Number.isFinite(Date.parse(value))&&new Date(value).toISOString().slice(0,10)===value;
 function bounds(dues,today,end,index){
  let min=shift(today,index);
  for(let j=0;j<index;j++){
   const previous=dues[j]?.date,maximum=latest(end,dues.length,j);
   if(validDate(previous)&&previous>=today&&(!maximum||previous<=maximum))min=[min,shift(previous,index-j)].sort().at(-1);
  }
  return {min,max:latest(end,dues.length,index)};
 }
 function rangeText(range){return 'Hạn đóng chỉ được chọn từ '+date(range.min)+(range.max?' đến '+date(range.max):' trở đi')+'.';}
 function validate(dues, total, today, end) {
  const errors = {};
  if (!dues?.length || dues.length > 3) return {installments:'Chỉ được chọn từ 1 đến 3 kỳ thanh toán.'};
  if(end&&end<today)return {installments:'Lớp đã kết thúc ngày '+date(end)+'. Không thể lập lịch đóng mới từ hôm nay '+date(today)+'.'};
  if(end&&end<shift(today,dues.length-1))return {installments:'Không đủ ngày để chia '+dues.length+' kỳ có hạn đóng khác nhau từ '+date(today)+' đến '+date(end)+'. Vui lòng chọn ít kỳ hơn.'};
  if (total <= 0) errors.installments='Học phí sau giảm phải lớn hơn 0đ để lập lịch đóng; không được tạo kỳ thanh toán 0đ.';
  let sum=0, valid=true;
  dues.forEach((due,i)=>{
   const key=`installments[${i}]`,period=`Kỳ ${i+1}`,raw=String(due.amount??'').trim(),amount=Number(raw);
   if(!raw) errors[key+'.amount']=period+': vui lòng nhập số tiền phải đóng.';
   else if(Number.isFinite(amount)&&amount<=0) errors[key+'.amount']=period+': số tiền phải lớn hơn 0đ. Nếu không cần kỳ này, hãy chọn ít kỳ hơn.';
   else if(Number.isFinite(amount)&&amount<1000) errors[key+'.amount']=period+': mỗi kỳ phải từ 1.000đ trở lên. Nếu học phí còn lại quá nhỏ, hãy chọn ít kỳ hơn.';
   else if(!/^\d+$/.test(raw)||!Number.isSafeInteger(amount)) errors[key+'.amount']=period+': số tiền phải là số nguyên đồng, không được nhập số thập phân.';
   else if(amount>9999999999) errors[key+'.amount']=period+': số tiền không được vượt giới hạn 9.999.999.999đ.';
   else if(amount>total) errors[key+'.amount']=period+': số tiền không được vượt học phí sau giảm '+money(total)+'.';
   if(errors[key+'.amount'])valid=false;else sum+=amount;
   if(!due.date) errors[key+'.date']=period+': vui lòng chọn hạn đóng.';
   else if(!/^\d{4}-\d{2}-\d{2}$/.test(due.date)||!Number.isFinite(Date.parse(due.date))||new Date(due.date).toISOString().slice(0,10)!==due.date) errors[key+'.date']=period+': ngày đóng không hợp lệ.';
   else if(end&&due.date>end) errors[key+'.date']=period+': hạn đóng '+date(due.date)+' vượt ngày kết thúc lớp '+date(end)+'. Vui lòng chọn ngày không sau '+date(end)+'.';
   else if(due.date<today) errors[key+'.date']=period+': hạn đóng không được trước hôm nay '+date(today)+'.';
   else if(end&&due.date>latest(end,dues.length,i)) errors[key+'.date']=period+': hạn đóng muộn nhất là '+date(latest(end,dues.length,i))+' để còn đủ ngày cho '+(dues.length-i-1)+' kỳ tiếp theo trước hoặc trong ngày kết thúc lớp '+date(end)+'. Vui lòng chọn ngày không sau '+date(latest(end,dues.length,i))+' hoặc giảm số kỳ thanh toán.';
   else if(due.date<bounds(dues,today,end,i).min){
    const previous=i?dues[i-1]?.date:'';
    const direct=validDate(previous)&&(!end||previous<=latest(end,dues.length,i-1))&&due.date<=previous;
    errors[key+'.date']=period+': '+(direct?'hạn đóng phải sau hạn kỳ '+i+' ('+date(previous)+'). ':'Các kỳ thanh toán phải theo thứ tự tăng dần. ')+rangeText(bounds(dues,today,end,i));
   }
  });
  if(valid&&total>0){
   if(sum!==total)errors.installments='Tổng các kỳ là '+money(sum)+', '+(sum<total?'còn thiếu ':'vượt ')+money(Math.abs(sum-total))+' so với học phí sau giảm '+money(total)+'.';
   // Integer arithmetic avoids rounding 30% upward by an extra dong due to binary floats.
   const minimum=Number((BigInt(total)*30n+99n)/100n);
   if(dues.length>1&&Number(dues[0].amount)<minimum)errors['installments[0].amount']='Kỳ 1 phải từ '+money(minimum)+' trở lên, tương đương tối thiểu 30% học phí sau giảm '+money(total)+'.';
  }
  return errors;
 }
 function discount(base,type,raw){
  const text=String(raw??'').trim();
  if(!text)throw new Error('Vui lòng nhập mức giảm học phí.');
  if(Number(text)<0)throw new Error('Mức giảm học phí không được là số âm.');
  if(!/^\d+$/.test(text))throw new Error('Mức giảm học phí phải là số nguyên, không được nhập số thập phân.');
  const value=Number(text);
  if(!Number.isSafeInteger(value))throw new Error('Mức giảm vượt giới hạn số nguyên được phép.');
  if(type==='NONE'){if(value!==0)throw new Error('Khi chọn Không giảm, mức giảm phải bằng 0.');return value;}
  if(type==='PERCENTAGE'){
   if(value===0)throw new Error('Phần trăm giảm phải từ 1% đến 30%. Nếu không áp dụng ưu đãi, hãy chọn Không giảm.');
   if(value>30)throw new Error('Phần trăm giảm không được vượt 30% học phí gốc.');
  }else if(type==='FIXED_AMOUNT'){
   if(base*30/100<1000)throw new Error('Học phí gốc này không đủ điều kiện giảm theo số tiền: 30% học phí thấp hơn mức giảm tối thiểu 1.000đ. Vui lòng chọn Không giảm hoặc giảm theo phần trăm.');
   if(value<1000)throw new Error('Số tiền giảm phải từ 1.000đ trở lên. Nếu không áp dụng ưu đãi, hãy chọn Không giảm.');
   const max=Number(BigInt(base)*30n/100n);
   if(value>max)throw new Error('Số tiền giảm không được vượt '+money(max)+', tương đương tối đa 30% học phí gốc '+money(base)+'.');
  }else throw new Error('Vui lòng chọn Không giảm, Giảm theo phần trăm hoặc Giảm theo số tiền.');
  return value;
 }
 root.tuitionScheduleValidation={validate,discount,shift,latest,bounds,rangeText};
 if(typeof module!=='undefined')module.exports=root.tuitionScheduleValidation;
})(typeof window==='undefined'?globalThis:window);
