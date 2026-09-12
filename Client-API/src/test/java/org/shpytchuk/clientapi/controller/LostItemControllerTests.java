package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.config.GraphQlConfig;
import org.shpytchuk.clientapi.config.GraphQlExceptionResolver;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.ContactInfoDto;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.input.ItemInput;
import org.shpytchuk.clientapi.service.FoundItemService;
import org.shpytchuk.clientapi.service.LostItemService;
import org.shpytchuk.clientapi.service.ReferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@GraphQlTest({LostItemController.class, FoundItemController.class, ReferenceController.class})
@Import({GraphQlConfig.class, GraphQlExceptionResolver.class})
class LostItemControllerTests {

    private static final ItemDto ITEM = new ItemDto(
            1L, "Ключі", "Звʼязка з брелоком", LocalDate.of(2026, 9, 1), 500, null,
            new CategoryDto(2L, "keys"),
            new PlaceDto(3L, "Площа Ринок"),
            new ContactInfoDto(4L, "+380671234567", "finder@example.com", List.of(SocialMediaEnum.TELEGRAM)));

    @Autowired
    private GraphQlTester tester;

    @MockitoBean
    private LostItemService lostItemService;

    @MockitoBean
    private FoundItemService foundItemService;

    @MockitoBean
    private ReferenceService referenceService;

    @Test
    void returnsLostItemById() {
        given(lostItemService.findById(1L)).willReturn(Optional.of(ITEM));

        tester.document("{ lostItem(id: 1) { id title date compensation category { key } place { name } contact { phone socialMedias } } }")
                .execute()
                .path("lostItem.title").entity(String.class).isEqualTo("Ключі")
                .path("lostItem.date").entity(String.class).isEqualTo("2026-09-01")
                .path("lostItem.category.key").entity(String.class).isEqualTo("keys")
                .path("lostItem.contact.socialMedias").entityList(String.class).containsExactly("TELEGRAM");
    }

    @Test
    void returnsFilteredPage() {
        given(lostItemService.findAll(any(), any(), any()))
                .willReturn(Window.from(List.of(ITEM), OffsetScrollPosition.positionFunction(0), false));

        tester.document("""
                        query($title: String) {
                          lostItems(filter: { title: $title }, sort: DATE_ASC, first: 5) {
                            edges { cursor node { id title } }
                            pageInfo { hasNextPage endCursor }
                          }
                        }
                        """)
                .variable("title", "клю")
                .execute()
                .path("lostItems.edges").entityList(Object.class).hasSize(1)
                .path("lostItems.edges[0].node.title").entity(String.class).isEqualTo("Ключі")
                .path("lostItems.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void createsLostItem() {
        given(lostItemService.create(any())).willReturn(ITEM);

        tester.document("""
                        mutation {
                          createLostItem(input: {
                            title: "Ключі"
                            date: "2026-09-01"
                            compensation: 500
                            categoryId: 2
                            placeId: 3
                            contact: { phone: "+380671234567", email: "finder@example.com", socialMedias: [TELEGRAM] }
                          }) { id title }
                        }
                        """)
                .execute()
                .path("createLostItem.id").entity(String.class).isEqualTo("1");

        var captor = org.mockito.ArgumentCaptor.forClass(ItemInput.class);
        verify(lostItemService).create(captor.capture());
        ItemInput sent = captor.getValue();
        assertThat(sent.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(sent.contact().email()).isEqualTo("finder@example.com");
    }

    @Test
    void rejectsInvalidPhone() {
        tester.document(
                        //Language=GraphQL
                        """
                                mutation {
                                  createLostItem(input: {
                                    title: "Ключі"
                                    date: "2026-09-01"
                                    compensation: 500
                                    categoryId: 2
                                    placeId: 3
                                    contact: { phone: "+380671234567", email: "finder@example.com", socialMedias: [TELEGRAM] }
                                  }) { id title }
                                }
                                
                                """)
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST)
                .verify();
    }


}
