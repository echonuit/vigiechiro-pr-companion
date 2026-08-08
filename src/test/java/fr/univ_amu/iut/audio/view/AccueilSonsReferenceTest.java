package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.view.Prisme;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La carte d'accueil « Sons & validation » ([AccueilSonsReference]).
///
/// Elle n'était citée dans **aucun** fichier de test (#3521). Son `ouvrir()` porte une subtilité qui
/// méritait d'être figée : l'identifiant de l'utilisateur est résolu **paresseusement**, à l'ouverture
/// et non à la construction de la carte - sinon le seul rendu de l'accueil exigerait un schéma migré.
class AccueilSonsReferenceTest {

    private static final Key<String> ID_COURANT = Key.get(String.class, Names.named("idUtilisateurCourant"));

    @Test
    @DisplayName("#3521 : ouvrir la carte ouvre le corpus de références de l'utilisateur courant")
    void ouvrir_ouvre_les_references_de_l_utilisateur() {
        OuvrirAudio ouvrirAudio = mock(OuvrirAudio.class);
        Injector injector = mock(Injector.class);
        when(injector.getInstance(ID_COURANT)).thenReturn("demo-enseignant");

        new AccueilSonsReference(ouvrirAudio, injector).ouvrir();

        verify(ouvrirAudio).ouvrir(new SourceObservations.References("demo-enseignant"));
    }

    @Test
    @DisplayName("#3521 : construire la carte ne touche PAS la base")
    void construire_la_carte_ne_resout_rien() {
        // La résolution paresseuse est la raison d'être du passage par l'injecteur plutôt que par
        // l'identifiant lui-même : construire l'accueil ne doit rien demander à la base.
        Injector injector = mock(Injector.class);

        new AccueilSonsReference(mock(OuvrirAudio.class), injector);

        verify(injector, org.mockito.Mockito.never()).getInstance(ID_COURANT);
    }

    @Test
    @DisplayName("#3521 : la carte annonce le prisme et le rang qui la placent sur l'accueil")
    void annonce_sa_place_sur_l_accueil() {
        AccueilSonsReference carte = new AccueilSonsReference(mock(OuvrirAudio.class), mock(Injector.class));

        assertThat(carte.prisme()).isEqualTo(Prisme.ESPECES_BIODIVERSITE);
        assertThat(carte.ordre()).isEqualTo(20);
        assertThat(carte.titre()).isEqualTo("Sons & validation");
    }
}
