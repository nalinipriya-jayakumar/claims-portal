package com.redrivermutual.notification.application.port.out;

import com.redrivermutual.notification.domain.ClaimConfirmation;

public interface EmailTemplateRenderer {

    RenderedEmail renderClaimConfirmation(ClaimConfirmation confirmation);
}
