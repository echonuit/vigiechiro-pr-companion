package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.RapprochementVigieChiro.Phase;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de l'**ordonnancement des rapprocheurs** (#1776) : la structure (sites, taxons) doit être rejouée
/// avant ce qui en dépend (les passages, rapatriés sur des points d'écoute déjà locaux), sinon un site tout
/// juste créé ne verrait ses passages qu'à la synchro suivante.
class RapprochementVigieChiroTest {

    private static RapprochementVigieChiro rapprocheur(String nom, Phase phase) {
        return new RapprochementVigieChiro() {
            @Override
            public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
                return Optional.empty();
            }

            @Override
            public Phase phase() {
                return phase;
            }

            @Override
            public String toString() {
                return nom;
            }
        };
    }

    @Test
    @DisplayName("Par défaut, un rapprocheur est en phase STRUCTURE (il n'attend rien d'un autre)")
    void phase_par_defaut_structure() {
        RapprochementVigieChiro sansPhaseDeclaree = client -> Optional.empty();

        assertThat(sansPhaseDeclaree.phase()).isEqualTo(Phase.STRUCTURE);
    }

    @Test
    @DisplayName("#2558 : un rapprocheur qui ignore le suivi reste appelé - la surcharge délègue au défaut")
    void surcharge_suivie_delegue_par_defaut() {
        List<ClientVigieChiro> appels = new java.util.ArrayList<>();
        // Un rapprocheur « court » (taxons, sites) n'implémente QUE la méthode abstraite. C'est toute la
        // raison d'avoir mis la variante suivie en `default` : ces rapprocheurs n'ont ni progression à
        // rendre ni travail à interrompre, et ne devaient pas être réécrits pour autant.
        RapprochementVigieChiro court = client -> {
            appels.add(client);
            return Optional.of(new RapportSynchro("taxons", 385));
        };

        Optional<RapportSynchro> rapport = court.synchroniser(null, progres -> {}, JetonAnnulation.neutre());

        assertThat(rapport).map(RapportSynchro::enClair).contains("385 taxons");
        assertThat(appels)
                .as("le rapprocheur a bien été invoqué par la surcharge")
                .hasSize(1);
    }

    @Test
    @DisplayName("#2558 : la surcharge n'a pas rompu le FunctionalInterface - une lambda reste un rapprocheur")
    void reste_une_interface_fonctionnelle() {
        assertThat(RapprochementVigieChiro.class.getAnnotation(FunctionalInterface.class))
                .as("deux méthodes abstraites feraient échouer toutes les lambdas du Multibinder")
                .isNotNull();
    }

    @Test
    @DisplayName("ordonnes place la structure avant les dépendants, quel que soit l'ordre d'entrée")
    void ordonnes_structure_avant_dependants() {
        RapprochementVigieChiro passages = rapprocheur("passages", Phase.DEPENDANTE);
        RapprochementVigieChiro sites = rapprocheur("sites", Phase.STRUCTURE);
        RapprochementVigieChiro taxons = rapprocheur("taxons", Phase.STRUCTURE);

        List<RapprochementVigieChiro> ordonnes = RapprochementVigieChiro.ordonnes(List.of(passages, sites, taxons));

        assertThat(ordonnes)
                .extracting(RapprochementVigieChiro::phase)
                .containsExactly(Phase.STRUCTURE, Phase.STRUCTURE, Phase.DEPENDANTE);
        assertThat(ordonnes.get(ordonnes.size() - 1))
                .as("les passages, qui dépendent des points locaux, passent en dernier")
                .isEqualTo(passages);
    }

    @Test
    @DisplayName("ordonnes est un tri STABLE : l'ordre d'origine est préservé au sein d'une même phase")
    void ordonnes_stable_dans_une_phase() {
        RapprochementVigieChiro sites = rapprocheur("sites", Phase.STRUCTURE);
        RapprochementVigieChiro taxons = rapprocheur("taxons", Phase.STRUCTURE);

        assertThat(RapprochementVigieChiro.ordonnes(List.of(sites, taxons))).containsExactly(sites, taxons);
        assertThat(RapprochementVigieChiro.ordonnes(List.of(taxons, sites))).containsExactly(taxons, sites);
    }
}
