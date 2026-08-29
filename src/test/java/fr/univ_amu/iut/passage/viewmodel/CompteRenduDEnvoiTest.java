package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel.Envoi;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que l'utilisateur lit quand l'envoi renonce. Un refus qui ne nomme pas le geste qui **marche**
/// laisse son lecteur en boucle : le patron est celui de #3854, sur le refus voisin « site non
/// rattaché », dans ce même chemin d'écriture.
@DisplayName("CompteRenduDEnvoi : le renoncement nomme le geste qui répare")
class CompteRenduDEnvoiTest {

    @Test
    @DisplayName("le renoncement conseille de RÉCUPÉRER, seul geste qui remet la base à jour")
    void le_renoncement_nomme_le_geste_qui_marche() {
        Envoi rendu = CompteRenduDEnvoi.de(new EnvoiParticipation.ModifieEntreTemps(Optional.empty()));

        // Rouvrir la fiche ne repart PAS de l'etat a jour : l'ADR 4640 a explicitement ecarte la
        // lecture distante a l'ouverture, si bien que la base ne bouge pas et que le renoncement se
        // reproduit a l'identique. Seul « Recuperer depuis Vigie-Chiro » appelle `tirerDepuis`, qui
        // note la base. Conseiller l'autre geste renvoie l'utilisateur dans un mur.
        assertThat(rendu).isInstanceOf(Envoi.Empeche.class);
        // Le libelle EXACT du bouton, tel que le FXML le porte : « Vigie-Chiro » avec trait d'union.
        // La premiere version de ce message citait « VigieChiro », un bouton qui n'existe pas sous ce
        // nom, et aucun test ne pouvait le voir - c'est la capture de la modale qui l'a montre. Nommer
        // un geste introuvable est aussi inutile que n'en nommer aucun.
        assertThat(rendu.retour().texte()).contains("Récupérer depuis Vigie-Chiro");
    }

    @Test
    @DisplayName("le renoncement date la divergence de notre LECTURE, pas de l'ouverture de la fiche")
    void le_renoncement_date_la_divergence_de_notre_lecture() {
        Envoi rendu = CompteRenduDEnvoi.de(new EnvoiParticipation.ModifieEntreTemps(Optional.empty()));

        // Depuis #4707 la comparaison porte sur la BASE, ce que la plateforme portait a notre derniere
        // lecture - qui peut dater de plusieurs jours et n'a aucun rapport avec l'ouverture de la
        // fiche. Dire « depuis que cette fiche est ouverte » fait chercher un collegue qui aurait
        // ecrit dans les dernieres minutes, alors qu'il a pu ecrire la semaine passee.
        assertThat(rendu.retour().texte()).doesNotContain("cette fiche est ouverte");
    }
}
