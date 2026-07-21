package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le porteur injectable de confirmation (#1013) : ce qu'il **délègue**, et surtout **comment**.
///
/// Tous les contrôleurs passent par lui (#1013). S'il ne redirige pas `confirmer(CompteRendu)` vers son
/// délégué, le compte rendu retombe sur le repli textuel du port et s'**aplatit** avant d'atteindre le vrai
/// dialogue - le rendu structuré de #2060 ne se verrait qu'en test et en capture, jamais en production
/// (#2223).
class ConfirmateurModifiableTest {

    /// Un délégué qui note **par quelle porte** on l'a appelé : la structurée ou la textuelle.
    private static final class DelegueEspion implements Confirmateur {
        private final AtomicReference<CompteRendu> parCompteRendu = new AtomicReference<>();
        private final AtomicReference<String> parTexte = new AtomicReference<>();

        @Override
        public boolean confirmer(String message) {
            parTexte.set(message);
            return true;
        }

        @Override
        public boolean confirmer(CompteRendu compteRendu) {
            parCompteRendu.set(compteRendu);
            return true;
        }
    }

    @Test
    @DisplayName("Un compte rendu est délégué TEL QUEL, sans être aplati en chaîne (#2223)")
    void delegue_le_compte_rendu_sans_l_aplatir() {
        DelegueEspion delegue = new DelegueEspion();
        ConfirmateurModifiable porteur = new ConfirmateurModifiable(delegue);
        CompteRendu rendu = CompteRendu.de("Suppression", List.of(Constat.de("Action irréversible.", Severite.ERREUR)));

        porteur.confirmer(rendu);

        assertThat(delegue.parCompteRendu.get())
                .as("le compte rendu atteint le délégué tel quel, pour qu'il le rende en structure")
                .isSameAs(rendu);
        assertThat(delegue.parTexte.get())
                .as("il n'est PAS mis à plat en chaîne avant d'y arriver")
                .isNull();
    }

    @Test
    @DisplayName("Une chaîne reste déléguée comme chaîne")
    void delegue_la_chaine_comme_chaine() {
        DelegueEspion delegue = new DelegueEspion();
        ConfirmateurModifiable porteur = new ConfirmateurModifiable(delegue);

        porteur.confirmer("Quitter cet écran ?");

        assertThat(delegue.parTexte.get()).isEqualTo("Quitter cet écran ?");
        assertThat(delegue.parCompteRendu.get()).isNull();
    }
}
