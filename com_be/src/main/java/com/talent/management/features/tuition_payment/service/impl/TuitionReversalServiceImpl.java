package com.talent.management.features.tuition_payment.service.impl;

import com.talent.management.features.tuition_payment.dto.request.ReviewTuitionRequest;
import com.talent.management.features.tuition_payment.exception.TuitionOperationException;
import com.talent.management.features.tuition_payment.service.TuitionReversalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

/** Compatibility adapter for retired cash-correction endpoints. Historical records remain intact. */
@Service
public class TuitionReversalServiceImpl implements TuitionReversalService {
    @Override
    public void request(Long paymentId, String reason) { throw retired(); }

    @Override
    public void review(Long id, ReviewTuitionRequest input) { throw retired(); }

    @Override
    public List<View> list() { throw retired(); }

    private TuitionOperationException retired() {
        return new TuitionOperationException(HttpStatus.GONE, "TUITION_CASH_CORRECTION_REMOVED",
                "Chức năng điều chỉnh thu nhầm đã được ngừng sử dụng.");
    }
}
