package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// **Envoyer un message au validateur** depuis l'écran (#1418) : le geste joué pour de vrai, jusqu'à
/// l'appel réseau (bouchonné).
///
/// L'enjeu de ces tests n'est pas que l'envoi marche. C'est qu'il **ne parte jamais sans que l'utilisateur
/// ait compris qu'il est définitif**. Le serveur ajoute par `$push` et n'offre **aucune** route de
/// suppression : un message posté par erreur restera sous les yeux d'un expert du MNHN, indéfiniment.
///
/// D'où l'insistance sur le **contenu** du message de confirmation, et non sur le fait qu'un dialogue
/// s'ouvre : un « êtes-vous sûr ? » générique ne serait pas un consentement.
///
/// ⚠️ **Aucune écriture réelle n'est tirée.** Une sonde live sur cette route serait irréversible.
@ExtendWith(ApplicationExtension.class)
class EnvoiMessageTest {

    private static final Long OBSERVATION = 7L;

    private final DiscussionValidateur discussion = mock(DiscussionValidateur.class);

    /// Ce que le confirmateur a **demandé** : le texte que l'utilisateur lit avant de dire oui.
    private final List<String> confirmations = new ArrayList<>();

    private final List<String> annonces = new ArrayList<>();
    private final List<NiveauNotification> niveaux = new ArrayList<>();

    private boolean confirme = true;

    private PanneauDiscussion panneau;

    @BeforeEach
    void preparer() {
        when(discussion.fil(any())).thenReturn(List.of());
        when(discussion.idProfilConnecte()).thenReturn("u-moi");
        when(discussion.pourquoiPasEcrire(any())).thenReturn(Optional.empty());

        panneau = new PanneauDiscussion();
        panneau.confirmateur().definir(nouveauConfirmateur());
        panneau.notificateur().definir(nouveauNotificateur());
        panneau.armerPourTest(discussion, new ExecuteurTacheSynchrone(), OBSERVATION);
    }

    @Test
    @DisplayName("#1418 : la confirmation DIT que le message est définitif, et cite le texte — on ne"
            + " consent qu'à ce qu'on a compris")
    void la_confirmation_dit_que_c_est_definitif() {
        when(discussion.poster(any(), anyString())).thenReturn(ReponseApi.succes("ok"));
        panneau.saisie().setText("Je doute de ce Pipkuh.");

        panneau.envoyer().fire();

        assertThat(confirmations)
                .singleElement()
                .asString()
                .as("le texte dit l'irréversibilité en toutes lettres, et rappelle ce qui va partir")
                .contains("ne pourra")
                .contains("supprimé")
                .contains("validateur")
                .contains("Je doute de ce Pipkuh.");
        verify(discussion).poster(OBSERVATION, "Je doute de ce Pipkuh.");
    }

    @Test
    @DisplayName("#1418 : refuser la confirmation → RIEN ne part, et le texte saisi est CONSERVÉ")
    void refuser_n_envoie_rien_et_garde_le_texte() {
        confirme = false;
        panneau.saisie().setText("Je me ravise.");

        panneau.envoyer().fire();

        verify(discussion, never()).poster(any(), anyString());
        assertThat(panneau.saisie().getText())
                .as("on ne fait pas perdre son texte à quelqu'un qui hésite")
                .isEqualTo("Je me ravise.");
    }

    @Test
    @DisplayName("#1418 : le serveur refuse → on le DIT, et le texte reste dans la zone de saisie")
    void un_refus_conserve_le_texte() {
        when(discussion.poster(any(), anyString()))
                .thenReturn(ReponseApi.refuse(404, "donnée introuvable (ancrage périmé)"));
        panneau.saisie().setText("Perdu d'avance.");

        panneau.envoyer().fire();

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .asString()
                .as("rien n'a été publié, et l'utilisateur ne doit pas croire le contraire")
                .contains("Rien n'a été publié");
        assertThat(panneau.saisie().getText()).isEqualTo("Perdu d'avance.");
    }

    @Test
    @DisplayName("#1418 : le message est parti → la zone de saisie se vide (elle serait sinon un piège à"
            + " double envoi, sur une route qui ne sait pas revenir en arrière)")
    void un_envoi_reussi_vide_la_saisie() {
        when(discussion.poster(any(), anyString())).thenReturn(ReponseApi.succes("ok"));
        panneau.saisie().setText("C'est parti.");

        panneau.envoyer().fire();

        assertThat(panneau.saisie().getText()).isEmpty();
    }

    private Confirmateur nouveauConfirmateur() {
        return message -> {
            confirmations.add(message);
            return confirme;
        };
    }

    private Notificateur nouveauNotificateur() {
        return (niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        };
    }
}
