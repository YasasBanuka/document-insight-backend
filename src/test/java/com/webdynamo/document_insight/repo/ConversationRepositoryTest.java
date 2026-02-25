package com.webdynamo.document_insight.repo;

import com.webdynamo.document_insight.model.Conversation;
import com.webdynamo.document_insight.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("findByUserIdOrderByUpdatedAtDesc - Should return conversations sorted by update time")
    void findByUserIdOrderByUpdatedAtDesc_ShouldSortCorrectly() {
        // --- ARRANGE ---
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("pass");
        entityManager.persist(user);

        Conversation convOld = new Conversation();
        convOld.setUser(user);
        convOld.setTitle("Old Conversation");
        entityManager.persist(convOld);
        
        // Force an older timestamp (PrePersist will set current time, so we update it)
        convOld.setUpdatedAt(LocalDateTime.now().minusDays(1));
        entityManager.merge(convOld);

        Conversation convNew = new Conversation();
        convNew.setUser(user);
        convNew.setTitle("New Conversation");
        entityManager.persist(convNew);

        entityManager.flush();

        // Use native query to bypass @PreUpdate/@PrePersist for testing
        entityManager.getEntityManager().createNativeQuery(
                "UPDATE conversation SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusDays(1))
                .setParameter("id", convOld.getId())
                .executeUpdate();

        entityManager.clear(); // Clear cache to force reload from DB

        // --- ACT ---
        List<Conversation> result = conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());

        // --- ASSERT ---
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("New Conversation");
        assertThat(result.get(1).getTitle()).isEqualTo("Old Conversation");
    }

    @Test
    @DisplayName("findByIdAndUserId - Should return conversation only if it belongs to the user")
    void findByIdAndUserId_ShouldEnforceOwnership() {
        // --- ARRANGE ---
        User owner = new User();
        owner.setEmail("owner@example.com");
        owner.setPassword("pass");
        entityManager.persist(owner);

        User stranger = new User();
        stranger.setEmail("stranger@example.com");
        stranger.setPassword("pass");
        entityManager.persist(stranger);

        Conversation conv = new Conversation();
        conv.setUser(owner);
        conv.setTitle("Private Chat");
        entityManager.persist(conv);

        // --- ACT & ASSERT ---
        // Owner should find it
        Optional<Conversation> found = conversationRepository.findByIdAndUserId(conv.getId(), owner.getId());
        assertThat(found).isPresent();

        // Stranger should NOT find it
        Optional<Conversation> notFound = conversationRepository.findByIdAndUserId(conv.getId(), stranger.getId());
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findByUpdatedAtBefore - Should find old conversations for cleanup")
    void findByUpdatedAtBefore_ShouldFindStaleData() {
        // --- ARRANGE ---
        User user = new User();
        user.setEmail("cleanup@example.com");
        user.setPassword("pass");
        entityManager.persist(user);

        LocalDateTime threshold = LocalDateTime.now().minusDays(30);

        Conversation stale = new Conversation();
        stale.setUser(user);
        stale.setTitle("Stale");
        entityManager.persist(stale);

        Conversation fresh = new Conversation();
        fresh.setUser(user);
        fresh.setTitle("Fresh");
        entityManager.persist(fresh);

        entityManager.flush();

        // Native update to set timestamps precisely for testing cleanup
        entityManager.getEntityManager().createNativeQuery(
                "UPDATE conversation SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", threshold.minusDays(1))
                .setParameter("id", stale.getId())
                .executeUpdate();

        entityManager.getEntityManager().createNativeQuery(
                "UPDATE conversation SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now())
                .setParameter("id", fresh.getId())
                .executeUpdate();

        entityManager.clear();

        // --- ACT ---
        List<Conversation> staleConvs = conversationRepository.findByUpdatedAtBefore(threshold);

        // --- ASSERT ---
        assertThat(staleConvs).hasSize(1);
        assertThat(staleConvs.get(0).getId()).isEqualTo(stale.getId());
    }
}
