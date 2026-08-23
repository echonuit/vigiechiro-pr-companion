package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// #4286 : l'audit complet interrogeait la base **une fois par nuit**, six requêtes chacune.
class RequetesDeLAuditTest {

    @TempDir
    Path dossier;

    /// Compte les connexions ouvertes : chaque requête des DAO en prend une.
    private static final class SourceComptee extends SourceDeDonnees {

        private final AtomicInteger connexions = new AtomicInteger();

        SourceComptee(Workspace workspace) {
            super(workspace);
        }

        @Override
        public Connection getConnection() {
            connexions.incrementAndGet();
            return super.getConnection();
        }
    }

    @Test
    @DisplayName("#4286 : quadrupler les nuits ne quadruple pas les requêtes")
    void les_requetes_ne_suivent_pas_les_nuits() {
        int pourQuatre = requetesPour(4);
        int pourSeize = requetesPour(16);

        // ⚠️ Le garde compte des REQUÊTES, pas des millisecondes - et pour cette mesure-ci c'était
        // indispensable : la machine qui a servi aux relevés portait un encodage vidéo à 676 % de CPU,
        // et les chronométrages allaient du simple au double d'un essai à l'autre. Un butoir en temps
        // n'aurait rien pu dire.
        //
        // Le défaut : six requêtes par nuit (session, originaux, séquences, journal, relevé, résultats).
        // Quatre des six sont désormais lues en tête, une fois pour toutes ([ContexteAudit]).
        int surcout = pourSeize - pourQuatre;
        System.out.printf(
                "%nREQUETES 4 nuits = %d | 16 nuits = %d | surcout = %d (%.1f par nuit)%n",
                pourQuatre, pourSeize, surcout, surcout / 12.0);
        // Relevé sur ce même test : **11 connexions par nuit** avant, **2** après. Le butoir est posé à
        // 4 par nuit - assez bas pour rougir si les lectures de tête repartaient dans la boucle, assez
        // haut pour ne pas rougir si une septième table s'ajoutait légitimement par session.
        assertThat(surcout)
                .as("douze nuits de plus ne doivent pas coûter douze fois onze requêtes")
                .isLessThan(12 * 4);
    }

    private int requetesPour(int nuits) {
        Path espace = dossier.resolve("ws-" + nuits);
        SourceComptee source = new SourceComptee(new Workspace(espace));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u", "S"));
        for (int i = 0; i < nuits; i++) {
            JeuDeDonneesPassage.dans(source)
                    .utilisateur("u")
                    .carre(String.format("%06d", 640000 + i))
                    .nomSite("C" + i)
                    .point("A1")
                    .semer();
        }
        int avant = source.connexions.get();
        new ServiceAuditCoherence(source, new Workspace(espace), Optional.empty(), Optional.empty()).auditerTout();
        return source.connexions.get() - avant;
    }
}
