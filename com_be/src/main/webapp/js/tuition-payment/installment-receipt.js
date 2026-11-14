// Presentation only. The amount and balance come from the saved transaction snapshot.
const tuitionReceipt = (() => {
 const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
 function node(tag,text,cls) {
  const el=document.createElement(tag);if(text!=null)el.textContent=text;if(cls)el.className=cls;return el;
 }
 function row(label,value,cls='') {
  const el=node('div',null,'receipt-line '+cls);el.append(node('span',label),node('strong',value??'—'));return el;
 }
 function render(invoice,receipt) {
  const paper=node('article',null,'tuition-receipt');paper.setAttribute('aria-label','Nội dung biên lai');
  const brand=node('header',null,'receipt-brand');
  brand.append(node('span','TC','receipt-mark'),node('h3','TALENT CENTER'),node('p','Trung tâm đào tạo năng khiếu'),node('h4','BIÊN LAI THU HỌC PHÍ'));
  paper.append(brand);
  const state=node('div',receipt.reversed?'ĐÃ ĐIỀU CHỈNH THU NHẦM':'ĐÃ THU THÀNH CÔNG','receipt-state '+(receipt.reversed?'is-reversed':''));
  paper.append(state);
  const when=receipt.at?receipt.at.split('T'):[];
  const date=when[0]?when[0].split('-').reverse().join('/'):'—';
  paper.append(row('Mã phiếu thu',receipt.code),row('Mã hóa đơn',invoice.code),row('Thời gian thu',date+(when[1]?' · '+when[1].slice(0,5):'')));
  const person=node('section',null,'receipt-person');
  person.append(row('Học viên',invoice.student),row('Phụ huynh',invoice.parent),row('Điện thoại',invoice.phone),row('Lớp học',invoice.className));paper.append(person);
  const cash=receipt.method==='CASH_AT_DESK';
  paper.append(row('Phương thức',cash?'Tiền mặt tại quầy':receipt.cashier==='PayOS tự động'?'Chuyển khoản · PayOS':'Chuyển khoản · Nhân viên xác nhận'),row(cash?'Người thu':'Nguồn xác nhận',receipt.cashier));
  paper.append(row('SỐ TIỀN LẦN THU NÀY',money(receipt.amount),'receipt-total'));
  if(cash){
   paper.append(row('Tiền khách đưa',money(receipt.cashGiven??receipt.amount)),row('Tiền trả lại thực tế',money(receipt.changeAmount??0)));
   const support=Number(receipt.cashRoundingAmount||0);
   if(support>0)paper.append(row('Trung tâm hỗ trợ làm tròn',money(support)),row('Tiền thực giữ tại quầy',money(Number(receipt.amount)-support)));
  }
  paper.append(row('Còn thiếu sau lần thu này',receipt.balanceAfter==null?'Chưa có dữ liệu lịch sử':money(receipt.balanceAfter),'receipt-balance'));
  paper.append(node('p',receipt.reversed?'Bản ghi này đã được đảo do nhập sai và không còn được tính vào số tiền đã thu. Biên lai gốc được giữ để tra cứu.':'Biên lai xác nhận khoản thu này. Số dư trên phiếu được lưu tại thời điểm giao dịch; các lần đóng tiếp theo có biên lai riêng.','receipt-note'));
  const footer=node('footer',null,'receipt-footer');footer.append(node('span','Người nộp tiền'),node('span','Người thu / Hệ thống xác nhận'));paper.append(footer);
  return paper;
 }
 return {render};
})();
