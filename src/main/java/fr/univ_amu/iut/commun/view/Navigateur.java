package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;

/// Service de navigation du socle : pilote la zone centrale du chrome **et** son historique.
///
/// Singleton Guice partagé par tout l'applicatif. Il tient un **historique de navigation** (pile des
/// écrans visités, base = Accueil) dont le sommet alimente [#vueCentraleProperty()] (liée par le
/// [MainController] au centre du `BorderPane`). Les écrans restent **vivants** dans la pile : revenir
/// ré-affiche l'instance précédente (état préservé), sans rechargement.
///
/// Deux notions distinctes (décision « fil d'Ariane hybride ») :
///  - le **← Retour** suit l'**historique** ([#revenir()]) ;
///  - le **fil d'Ariane** ([#filActuel()]) suit l'**emplacement** hiérarchique de l'écran courant quand
///    il le déclare ([EmplacementNavigation]), sinon retombe sur l'historique.
///
/// Avant toute sortie d'écran, une éventuelle [GardeQuitter] est consultée (saisie non enregistrée →
/// confirmation via [ConfirmateurQuitter]). Le verrou dur
/// [NavigationViewModel#navigationVerrouilleeProperty] (#54, import en cours) bloque toute navigation.
@Singleton
public class Navigateur {

    private final NavigationViewModel navigation;
    private final ObservableList<EtapeNavigation> historique = FXCollections.observableArrayList();
    // Vue lecture seule STABLE (champ, pas un wrapper recréé à chaque appel) : sinon le wrapper
    // temporaire, faiblement référencé, est ramassé par le GC et le listener du chrome cesse de
    // notifier (la barre de navigation ne se reconstruirait plus).
    private final ObservableList<EtapeNavigation> historiqueLectureSeule =
            FXCollections.unmodifiableObservableList(historique);
    private final ObjectProperty<Parent> vueCentrale = new SimpleObjectProperty<>(this, "vueCentrale");
    private ConfirmateurQuitter confirmateur = new ConfirmationNavigation();
    private EtapeNavigation accueil;

    @Inject
    public Navigateur(NavigationViewModel navigation) {
        this.navigation = navigation;
    }

    /// Propriété observable de la vue centrale courante (sommet de l'historique). Le [MainController] y
    /// lie le centre du `BorderPane`.
    public ObjectProperty<Parent> vueCentraleProperty() {
        return vueCentrale;
    }

    public Parent getVueCentrale() {
        return vueCentrale.get();
    }

    /// Historique de navigation observable, en **lecture seule** (pour le chrome).
    public ObservableList<EtapeNavigation> historique() {
        return historiqueLectureSeule;
    }

    /// Vrai s'il existe un écran précédent (le ← Retour est alors actionnable).
    public boolean peutRevenir() {
        return historique.size() > 1;
    }

    /// Libellé de l'écran vers lequel le ← Retour ramène (étape précédente de l'**historique**), pour
    /// l'afficher sur le bouton (« ← Vue multi-sites ») et lever toute ambiguïté quand le fil d'Ariane
    /// montre l'emplacement plutôt que la route. `null` à l'accueil (pas de retour).
    public String libelleRetour() {
        return historique.size() < 2
                ? null
                : historique.get(historique.size() - 2).libelle();
    }

    /// Stratégie de confirmation « quitter malgré une saisie » (injectable pour les tests).
    void setConfirmateurQuitter(ConfirmateurQuitter confirmateur) {
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
    }

    /// Mémorise la vue d'accueil et **initialise l'historique** à `[Accueil]`. Appelée une fois par le
    /// [MainController] au démarrage.
    public void memoriserAccueil(Parent accueilVue) {
        this.accueil = new EtapeNavigation("accueil", "Accueil", accueilVue, null);
        historique.setAll(this.accueil);
        synchroniser();
    }

    /// Ouvre `vue` comme **racine** d'un parcours : réinitialise l'historique à `[Accueil, écran]`.
    /// Utilisé par les entrées depuis une carte d'accueil.
    public void ouvrirRacine(Parent vue, String id, String libelle, Object controleur) {
        if (!peutQuitterCourant()) {
            return;
        }
        EtapeNavigation etape = new EtapeNavigation(id, libelle, vue, controleur);
        if (accueil != null) {
            historique.setAll(accueil, etape);
        } else {
            historique.setAll(etape);
        }
        synchroniser();
    }

    /// Empile `vue` (drill-down). **Anti-ré-entrance** : si un écran de même `id` est déjà dans la pile,
    /// on dépile jusqu'à lui et on le rafraîchit (évite les boucles qui gonfleraient le fil).
    public void empiler(Parent vue, String id, String libelle, Object controleur) {
        if (!peutQuitterCourant()) {
            return;
        }
        EtapeNavigation etape = new EtapeNavigation(id, libelle, vue, controleur);
        int existant = indexDe(id);
        if (existant >= 0) {
            while (historique.size() > existant + 1) {
                historique.remove(historique.size() - 1);
            }
            historique.set(existant, etape);
        } else {
            historique.add(etape);
        }
        synchroniser();
    }

    /// Compat : équivaut à un [#empiler] sans controller (ni garde ni emplacement déclarés).
    public void afficher(Parent vue, String id, String libelle) {
        empiler(vue, id, libelle, null);
    }

    /// ← Retour : dépile d'un cran vers l'écran précédent réel (historique). Sans effet à l'accueil.
    public void revenir() {
        if (historique.size() <= 1 || !peutQuitterCourant()) {
            return;
        }
        historique.remove(historique.size() - 1);
        synchroniser();
    }

    /// Dépile jusqu'à l'étape `index` de l'historique (clic d'un segment de fil en mode repli).
    public void revenirAIndex(int index) {
        if (index < 0 || index >= historique.size() - 1 || !peutQuitterCourant()) {
            return;
        }
        while (historique.size() > index + 1) {
            historique.remove(historique.size() - 1);
        }
        synchroniser();
    }

    /// Réaffiche l'accueil global (dépile tout). Neutralisé tant que la navigation est verrouillée (#54).
    public void afficherAccueil() {
        if (accueil == null || navigation.isNavigationVerrouillee() || !peutQuitterCourant()) {
            return;
        }
        historique.setAll(accueil);
        synchroniser();
    }

    /// Le **fil d'Ariane** courant : l'emplacement déclaré par l'écran ([EmplacementNavigation], préfixé
    /// d'« Accueil ») s'il existe, sinon un repli sur l'historique réel (chaque ancêtre cliquable).
    public List<Lieu> filActuel() {
        if (historique.isEmpty()) {
            return List.of();
        }
        EtapeNavigation sommet = historique.get(historique.size() - 1);
        EmplacementNavigation emplacement = sommet.emplacement();
        if (emplacement != null) {
            List<Lieu> fil = new ArrayList<>();
            fil.add(Lieu.vers("Accueil", this::afficherAccueil));
            fil.addAll(emplacement.emplacement());
            return List.copyOf(fil);
        }
        List<Lieu> fil = new ArrayList<>();
        for (int i = 0; i < historique.size(); i++) {
            String libelle = historique.get(i).libelle();
            if (i == historique.size() - 1) {
                fil.add(Lieu.courant(libelle));
            } else {
                int index = i;
                fil.add(Lieu.vers(libelle, () -> revenirAIndex(index)));
            }
        }
        return List.copyOf(fil);
    }

    private int indexDe(String id) {
        for (int i = 0; i < historique.size(); i++) {
            if (historique.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean peutQuitterCourant() {
        if (navigation.isNavigationVerrouillee()) {
            return false;
        }
        if (historique.isEmpty()) {
            return true;
        }
        GardeQuitter garde = historique.get(historique.size() - 1).garde();
        if (garde != null && garde.aSaisieNonEnregistree()) {
            return confirmateur.confirmer(garde.messageConfirmationQuitter());
        }
        return true;
    }

    private void synchroniser() {
        if (historique.isEmpty()) {
            return;
        }
        EtapeNavigation sommet = historique.get(historique.size() - 1);
        vueCentrale.set(sommet.vue());
        navigation.setVueCourante(sommet.id());
        navigation.setFilAriane(filActuel().stream().map(Lieu::libelle).collect(Collectors.joining(" › ")));
    }
}
