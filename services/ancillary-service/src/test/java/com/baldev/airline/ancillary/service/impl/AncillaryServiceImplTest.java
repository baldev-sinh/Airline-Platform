package com.baldev.airline.ancillary.service.impl;

import com.baldev.airline.ancillary.client.AirlineClient;
import com.baldev.airline.ancillary.model.Ancillary;
import com.baldev.airline.ancillary.repository.AncillaryRepository;
import com.baldev.airline.ancillary.repository.InsuranceCoverageRepository;
import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.ancillary.payload.request.AncillaryRequest;
import com.baldev.airline.ancillary.client.dto.AirlineResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AncillaryServiceImplTest {

    private static final long OWNER_USER_ID = 30L;
    private static final long AIRLINE_ID = 10L;

    private final AncillaryRepository ancillaries = mock(AncillaryRepository.class);
    private final AirlineClient airlineClient = mock(AirlineClient.class);
    private final AncillaryServiceImpl service =
            new AncillaryServiceImpl(ancillaries, mock(InsuranceCoverageRepository.class), airlineClient);

    @Test
    void newExtraBelongsToTheAirlineNotToItsOwnersUserId() {
        // The owner's user id and the airline id differ. Storing the user id made an airline's extras invisible
        // to it (lists filter by airline id) and showed them to whichever airline had that id instead.
        when(airlineClient.getAirlineByOwner(OWNER_USER_ID))
                .thenReturn(AirlineResponse.builder().id(AIRLINE_ID).ownerId(OWNER_USER_ID).build());
        when(ancillaries.save(any(Ancillary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAncillary(OWNER_USER_ID, AncillaryRequest.builder()
                .type(AncillaryType.BAGGAGE).subType("EXTRA_BAG").name("Extra 15 kg checked bag").build());

        ArgumentCaptor<Ancillary> saved = ArgumentCaptor.forClass(Ancillary.class);
        verify(ancillaries).save(saved.capture());
        assertThat(saved.getValue().getAirlineId()).isEqualTo(AIRLINE_ID);
    }
}
