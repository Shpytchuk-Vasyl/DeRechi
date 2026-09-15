package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.FoundItem;
import org.shpytchuk.clientapi.event.ItemCreatedEvent;
import org.shpytchuk.clientapi.repository.FoundItemRepository;
import org.shpytchuk.clientapi.repository.LostItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.execution.ErrorType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class FoundItemControllerTests extends AbstractGraphQlTests {

    private static final String CREATE = """
            mutation Create($input: ItemInput!) {
              createFoundItem(input: $input) { id title image place { name } }
            }
            """;

    @Autowired
    private FoundItemRepository foundItemRepository;

    @Autowired
    private LostItemRepository lostItemRepository;

    @Test
    void createsFoundItemRowInTheDatabase() {
        Long id = create("Парасолька");

        FoundItem stored = foundItemRepository.findWithDetailsById(id).orElseThrow();

        assertThat(stored.getTitle()).isEqualTo("Парасолька");
        assertThat(stored.getImage()).isEqualTo("keys.png");
        assertThat(stored.getPlace().getName()).isEqualTo("Площа Ринок");
    }

    @Test
    void keepsFoundItemsOutOfTheLostTable() {
        create("Парасолька");

        assertThat(foundItemRepository.findAll()).hasSize(1);
        assertThat(lostItemRepository.findAll()).isEmpty();
    }

    @Test
    void publishesTheFoundRoutingKey() {
        create("Парасолька");

        verify(rabbitTemplate).convertAndSend(
                eq("derechi.items"), eq("item.found.created"), any(ItemCreatedEvent.class));
    }

    @Test
    void rejectsAFoundItemWithoutAnImage() {
        Map<String, Object> input = input("Парасолька", documents.getId());
        input.remove("image");

        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains("Image is required");
                });

        assertThat(foundItemRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsABlankImage() {
        Map<String, Object> input = input("Парасолька", documents.getId());
        input.put("image", "   ");

        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    @Test
    void readsBackTheFoundItem() {
        Long id = create("Парасолька");

        tester.document("""
                        query Read($id: ID!) {
                          foundItem(id: $id) { title image contact { socialMedias } }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("foundItem.title").entity(String.class).isEqualTo("Парасолька")
                .path("foundItem.contact.socialMedias").entityList(String.class)
                .containsExactly("TELEGRAM");
    }

    @Test
    void listsFoundItemsSeparatelyFromLostOnes() {
        create("Парасолька");

        tester.document("{ foundItems(first: 10) { edges { node { title } } } }")
                .execute()
                .path("foundItems.edges[*].node.title").entityList(String.class)
                .containsExactly("Парасолька");

        tester.document("{ lostItems(first: 10) { edges { node { title } } } }")
                .execute()
                .path("lostItems.edges").entityList(Object.class).hasSize(0);
    }

    @Test
    void rejectsPhoneThatIsNotE164() {
        Map<String, Object> input = input("Парасолька", documents.getId());
        input.put("contact", contact("380671234567"));

        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains("phone");
                });
    }

    private Long create(String title) {
        return tester.document(CREATE)
                .variable("input", input(title, documents.getId()))
                .execute()
                .path("createFoundItem.id").entity(Long.class).get();
    }
}
