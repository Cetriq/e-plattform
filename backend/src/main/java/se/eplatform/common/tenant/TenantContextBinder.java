package se.eplatform.common.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Hands the organisation and user to the database at the start of every
 * transaction, for row level security (migration V14).
 *
 * This is the only place the context is set. set_config(..., true) makes it
 * local to the transaction: it is cleared at commit and rollback and never
 * travels with a pooled connection to the next request. If it cannot be set,
 * the transaction does not start. The values are bound as parameters.
 */
public class TenantContextBinder extends JpaTransactionManager {

    public TenantContextBinder(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        holder.getEntityManager()
                .createNativeQuery("SELECT set_config('app.org_id', :org, true), set_config('app.user_id', :user, true)")
                .setParameter("org", TenantContext.organization().map(UUID::toString).orElse(""))
                .setParameter("user", TenantContext.user().map(UUID::toString).orElse(""))
                .getSingleResult();
    }
}
