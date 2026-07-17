package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [SynchronisationSites] (#1808) : le bouton « Mes sites » rejoue la structure des sites
/// **puis** ses dépendants (squelettes de nuits, #1662), dans cet ordre, et agrège leurs comptes-rendus.
class SynchronisationSitesTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    /// Rapprocheur espion : note son passage dans `journal` (pour prouver l'ordre) et renvoie le rapport
    /// configuré, en mémorisant le client reçu.
    private static final class RapprocheurEspion implements RapprochementVigieChiro {
        private final String nom;
        private final Optional<RapportSynchro> rapport;
        private final List<String> journal;
        private ClientVigieChiro clientVu;

        RapprocheurEspion(String nom, Optional<RapportSynchro> rapport, List<String> journal) {
            this.nom = nom;
            this.rapport = rapport;
            this.journal = journal;
        }

        @Override
        public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
            journal.add(nom);
            this.clientVu = client;
            return rapport;
        }
    }

    @Test
    @DisplayName("#1808 : la structure des sites est rejouée AVANT ses dépendants (points avant squelettes)")
    void sites_avant_dependants() {
        List<String> journal = new ArrayList<>();
        RapprocheurEspion sites = new RapprocheurEspion("sites", Optional.of(new RapportSynchro("sites", 2)), journal);
        RapprocheurEspion passages = new RapprocheurEspion(
                "passages", Optional.of(new RapportSynchro("passage(s) rapatrié(s)", 5)), journal);
        SynchronisationSites synchro = new SynchronisationSites(sites, List.of(passages), client);

        List<RapportSynchro> rapports = synchro.synchroniser();

        assertThat(journal)
                .as("les sites (structure) précèdent leurs dépendants : les squelettes exigent des points locaux")
                .containsExactly("sites", "passages");
        assertThat(rapports).extracting(RapportSynchro::libelle).containsExactly("sites", "passage(s) rapatrié(s)");
        assertThat(sites.clientVu)
                .as("le même client est passé à chaque rapprocheur")
                .isSameAs(client);
        assertThat(passages.clientVu).isSameAs(client);
    }

    @Test
    @DisplayName("#1808 : un rapprocheur sans rien à dire (Optional vide) est sollicité mais n'ajoute pas de rapport")
    void rapprocheur_muet_ignore() {
        List<String> journal = new ArrayList<>();
        RapprocheurEspion sites = new RapprocheurEspion("sites", Optional.of(new RapportSynchro("sites", 1)), journal);
        RapprocheurEspion passagesMuet = new RapprocheurEspion("passages", Optional.empty(), journal);
        SynchronisationSites synchro = new SynchronisationSites(sites, List.of(passagesMuet), client);

        List<RapportSynchro> rapports = synchro.synchroniser();

        assertThat(journal).as("le dépendant est bien sollicité, même muet").containsExactly("sites", "passages");
        assertThat(rapports).extracting(RapportSynchro::libelle).containsExactly("sites");
    }

    @Test
    @DisplayName("#1045 : sans dépendant déclaré, seul le rapport des sites est rendu")
    void sites_seuls() {
        List<String> journal = new ArrayList<>();
        RapprocheurEspion sites = new RapprocheurEspion("sites", Optional.of(new RapportSynchro("sites", 3)), journal);
        SynchronisationSites synchro = new SynchronisationSites(sites, List.of(), client);

        assertThat(synchro.synchroniser()).extracting(RapportSynchro::libelle).containsExactly("sites");
    }
}
