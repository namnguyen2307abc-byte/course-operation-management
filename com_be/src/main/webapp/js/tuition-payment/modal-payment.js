/** Keep existing POS entry links pointing to the shared installment collection screen. */
export async function openPaymentModal(invoiceId) {
    const prefix = window.location.pathname.startsWith('/com_fe/') ? '/com_fe' : '';
    window.location.href = prefix + '/pages/tuition-receivables.html?invoiceId=' + encodeURIComponent(invoiceId);
}
