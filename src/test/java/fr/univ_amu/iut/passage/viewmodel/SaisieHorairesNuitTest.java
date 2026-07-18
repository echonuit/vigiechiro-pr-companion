package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Saisie des **heures de la nuit** (#1892), le sous-ViewModel extrait de `SaisiePassageConditions`.
///
/// Trou trouvé à la **passe 6** de la clôture : `ServiceConditionsPassage.definirHoraires` était couvert,
/// mais la couche au-dessus - normalisation à l'affichage, snapshot de ce qui a été chargé, décision de
/// ne pas réécrire - ne l'était par rien. Elle porte pourtant deux règles qui lui sont propres.
class SaisieHorairesNuitTest {

    private final ServiceConditionsPassage service = mock(ServiceConditionsPassage.class);
    private final MessagesRattachement messages = new MessagesRattachement();
    private final SaisieHorairesNuit saisie = new SaisieHorairesNuit(service, messages);

    private static Passage passage(String debut, String fin) {
        return new Passage(1L, 1, 2026, "2026-07-04", debut, fin, null, null, null, null, null, null, 1L, "1925492");
    }

    @Test
    @DisplayName("Les heures s'affichent au format du champ : « 20:25:00 » en base devient « 20:25 »")
    void charger_normalise_le_format() {
        saisie.charger(1L, "20:25:00", "07:47:00");

        // Les nuits importées portent les secondes, celles saisies ici non : sans mise en forme, deux
        // nuits voisines s'affichaient différemment, et « 20:25:00 » démentait le « ex. 21:00 » du champ.
        assertThat(saisie.debutProperty().get()).isEqualTo("20:25");
        assertThat(saisie.finProperty().get()).isEqualTo("07:47");
    }

    @Test
    @DisplayName("Une heure illisible s'affiche telle quelle : c'est le seul indice de ce qu'il faut corriger")
    void charger_laisse_passer_une_heure_illisible() {
        saisie.charger(1L, "n'importe quoi", null);

        assertThat(saisie.debutProperty().get()).isEqualTo("n'importe quoi");
        assertThat(saisie.finProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Nuit attestée : les champs sont marqués prouvés, et « Appliquer » n'écrit rien")
    void nuit_prouvee_n_ecrit_pas() {
        when(service.heuresProuvees(1L)).thenReturn(true);
        saisie.charger(1L, "21:00", "06:00");

        assertThat(saisie.prouveesProperty().get()).isTrue();
        assertThat(saisie.enregistrer()).isTrue();
        verify(service, never()).definirHoraires(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Saisie inchangée : « Appliquer » ne réécrit pas des heures que personne n'a touchées")
    void saisie_inchangee_n_ecrit_pas() {
        when(service.heuresProuvees(1L)).thenReturn(false);
        saisie.charger(1L, "21:00:00", "06:00:00");

        assertThat(saisie.enregistrer()).isTrue();
        // Piège : `charger` a normalisé « 21:00:00 » en « 21:00 ». Comparer la saisie à la valeur BRUTE
        // ferait croire à un changement à chaque ouverture de la modale, et déclencherait une écriture.
        verify(service, never()).definirHoraires(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Saisie modifiée : les heures sont écrites, et la ligne de message est nettoyée")
    void saisie_modifiee_ecrit() {
        when(service.heuresProuvees(1L)).thenReturn(false);
        when(service.definirHoraires(1L, "21:00", "06:00")).thenReturn(passage("21:00", "06:00"));
        saisie.charger(1L, "15:00", "15:00");
        messages.erreur("un reste du geste précédent");

        saisie.debutProperty().set("21:00");
        saisie.finProperty().set("06:00");

        assertThat(saisie.enregistrer()).isTrue();
        verify(service).definirHoraires(1L, "21:00", "06:00");
        assertThat(messages.retourProperty().get().texte()).isEmpty();
    }

    @Test
    @DisplayName("Saisie refusée : le motif est publié, et « Appliquer » ne ferme pas la modale")
    void saisie_refusee_publie_le_motif() {
        when(service.heuresProuvees(1L)).thenReturn(false);
        when(service.definirHoraires(1L, "21:00", "21:00"))
                .thenThrow(new RegleMetierException("L'heure de fin ne peut pas être identique à l'heure de début"));
        saisie.charger(1L, "21:00", "06:00");

        saisie.finProperty().set("21:00");

        assertThat(saisie.enregistrer()).isFalse();
        assertThat(messages.retourProperty().get().texte()).contains("identique à l'heure de début");
    }

    @Test
    @DisplayName("Après une écriture réussie, un second « Appliquer » n'écrit plus rien")
    void seconde_application_sans_changement() {
        when(service.heuresProuvees(1L)).thenReturn(false);
        when(service.definirHoraires(1L, "21:00", "06:00")).thenReturn(passage("21:00", "06:00"));
        saisie.charger(1L, "15:00", "15:00");
        saisie.debutProperty().set("21:00");
        saisie.finProperty().set("06:00");
        saisie.enregistrer();

        assertThat(saisie.enregistrer()).isTrue();

        // Une seule écriture, pas deux : le snapshot suit la saisie enregistrée.
        verify(service).definirHoraires(1L, "21:00", "06:00");
    }
}
