package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Parent;

/// Service de navigation du socle : pilote la zone centrale du chrome **et** son historique.
///
/// Singleton Guice partagé par tout l'applicatif. Il tient un **historique de navigation** (pile des
/// écrans visités, base = Accueil) dont le sommet alimente [#vueCentraleProperty()] (liée par le
/// [MainController] au centre du `BorderPane`). Les écrans restent **vivants** dans la pile : revenir
/// ré-affiche l'instance précédente (état préservé), sans rechargement — sauf si l'écran déclare le
/// contrat [RafraichirAuRetour], auquel cas il est rechargé au retour (p. ex. M-Passage, dont une
/// sous-activité comme M-Qualification a pu modifier l'état pendant qu'il était masqué).
///
/// Deux notions distinctes (décision « fil d'Ariane hybride ») :
///  - le **← Retour** suit l'**historique** ([#revenir()]) ;
///  - le **fil d'Ariane** ([#filActuel()]) suit l'**emplacement** hiérarchique de l'écran courant quand
///    il le déclare ([EmplacementNavigation]), sinon retombe sur l'historique.
///
/// Avant toute sortie d'écran, une éventuelle [GardeQuitter] est consultée (saisie non enregistrée →
/// confirmation via [Confirmateur]). Le verrou dur
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
    private Confirmateur confirmateur = new ConfirmationNavigation();
    private EtapeNavigation accueil;

    @Inject
    public Navigateur(NavigationViewModel navigation) {
        this.navigation = navigation;
        // Hook de **départ d'écran** (#230) : quand une étape est retirée de l'historique (retour, retour
        // accueil, nouvelle racine, clic d'un ancêtre), on prévient son controller s'il déclare
        // [AuDepartEcran], pour qu'il libère ses ressources (ex. l'import supprime son temporaire .zip).
        // Un seul listener couvre toutes les opérations qui dépilent. Le nettoyage ne doit jamais lever.
        historique.addListener((ListChangeListener<EtapeNavigation>) changement -> {
            while (changement.next()) {
                if (!changement.wasRemoved()) {
                    continue;
                }
                for (EtapeNavigation retiree : changement.getRemoved()) {
                    // Un `setAll` re-place l'accueil (retiré puis ré-ajouté) : on ne notifie pas un écran
                    // encore présent dans l'historique (il n'est pas réellement quitté).
                    if (retiree.auDepartEcran() != null && !historique.contains(retiree)) {
                        retiree.auDepartEcran().auDepartEcran();
                    }
                }
            }
        });
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
    void setConfirmateurQuitter(Confirmateur confirmateur) {
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
        rafraichirSommetAuRetour();
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
        rafraichirSommetAuRetour();
    }

    /// Réaffiche l'accueil global (dépile tout). Comme toute sortie d'écran, avertit si une opération
    /// critique est en cours ou une saisie non enregistrée est en attente (#906/#54).
    public void afficherAccueil() {
        if (accueil == null || !peutQuitterCourant()) {
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
        return peutQuitter("Une tâche est en cours (%s). Changer d'écran maintenant risque de la laisser"
                + " dans un état incohérent. Quitter quand même ?");
    }

    /// Consulté avant de **fermer l'application** (`App.setOnCloseRequest`, #906) : si une opération
    /// critique est en cours (import, génération d'archives, dépôt) ou une saisie non enregistrée est en
    /// attente, demande confirmation. Renvoie `true` si la fermeture peut se poursuivre.
    public boolean confirmerFermeture() {
        return peutQuitter("Une tâche est en cours (%s). Fermer l'application l'interrompra et risque de"
                + " laisser un état incohérent. Fermer quand même ?");
    }

    /// Cœur commun de « quitter l'écran » et « fermer l'app » : avertit (mais laisse passer si confirmé)
    /// d'une opération critique en cours, puis d'une saisie non enregistrée (garde de l'écran courant).
    /// `messageSiOperation` porte un `%s` où insérer le libellé de l'opération.
    private boolean peutQuitter(String messageSiOperation) {
        String operation = navigation.operationCritique();
        if (!operation.isBlank() && !confirmateur.confirmer(messageSiOperation.formatted(operation))) {
            return false;
        }
        if (historique.isEmpty()) {
            return true;
        }
        GardeQuitter garde = historique.get(historique.size() - 1).garde();
        return garde == null
                || !garde.aSaisieNonEnregistree()
                || confirmateur.confirmer(garde.messageConfirmationQuitter());
    }

    private void synchroniser() {
        if (historique.isEmpty()) {
            return;
        }
        EtapeNavigation sommet = historique.get(historique.size() - 1);
        vueCentrale.set(sommet.vue());
        navigation.setVueCourante(sommet.id());
        navigation.setFilAriane(filActuel().stream().map(Lieu::libelle).collect(Collectors.joining(" › ")));

        // Barre de statut : les zones du pied suivent le **résumé** de l'écran au sommet s'il en déclare
        // un ([ResumeStatut], lié pour une mise à jour en direct), superposé sur le défaut du chrome (une
        // zone non renseignée par l'écran garde le défaut). Sinon on revient au défaut (zones vides → le
        // chrome masque la barre). Centralisé ici (appelé à chaque changement d'écran), donc aucun
        // nettoyage par écran n'est requis.
        navigation.zonesStatutProperty().unbind();
        ResumeStatut resume = sommet.resumeStatut();
        if (resume != null) {
            navigation
                    .zonesStatutProperty()
                    .bind(Bindings.createObjectBinding(
                            () -> ZonesStatut.superposer(
                                    NavigationViewModel.ZONES_DEFAUT,
                                    resume.zonesStatutProperty().get()),
                            resume.zonesStatutProperty()));
        } else {
            navigation.setZonesStatut(NavigationViewModel.ZONES_DEFAUT);
        }
    }

    /// Recharge le sommet courant s'il déclare [RafraichirAuRetour]. Appelé uniquement par les
    /// **retours** ([#revenir], [#revenirAIndex]) : un écran qu'on ré-affiche peut montrer des données
    /// modifiées par une sous-activité pendant qu'il était masqué. Les ouvertures « avant » (empiler,
    /// ouvrirRacine) chargent déjà un écran neuf, donc n'ont pas besoin de ce rappel.
    private void rafraichirSommetAuRetour() {
        if (historique.isEmpty()) {
            return;
        }
        RafraichirAuRetour hook = historique.get(historique.size() - 1).rafraichirAuRetour();
        if (hook != null) {
            hook.rafraichirAuRetour();
        }
    }
}
