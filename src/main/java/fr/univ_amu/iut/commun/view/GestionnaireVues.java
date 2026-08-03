package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Pane;

/// Barre d'**onglets de vues mémorisées** « à la Notion » (#623), au-dessus d'une table : un onglet par
/// vue enregistrée de la `feature`, plus un bouton **« + Vue »**. Cliquer un onglet **rejoue** sa
/// combinaison de filtres (via [GestionnaireFiltres#restaurer(DescripteurFiltre)]) ; « + Vue » enregistre
/// les filtres **courants** (via [GestionnaireFiltres#decrire()]) ; chaque onglet offre renommer (✎) et
/// supprimer (✕).
///
/// Générique sur le type de ligne `T` (il ne connaît que le [GestionnaireFiltres] qu'il pilote). Socle
/// partagé (`commun`) des vues tabulaires : multisite d'abord, puis audio / analyse. La **saisie du nom**
/// (nouvelle vue ou renommage) dépend de l'IHM de l'écran hôte : elle est **injectée** (`saisieNom`), ce
/// qui garde ce gestionnaire indépendant de tout dialogue concret et **testable** sans boîte modale.
///
/// @param <T> type des lignes filtrées (ex. `LignePassage`)
public final class GestionnaireVues<T> {

    private final Pane onglets;

    /// Rendu de la barre (#3056, extraction) : ce gestionnaire tient le cycle de vie des vues, pas leur
    /// apparence.
    private final OngletsVues barre;
    private final GestionnaireFiltres<T> filtres;
    private final DepotVues depot;
    private final String feature;
    private final Function<String, Optional<String>> saisieNom;

    /// Compte rendu de restauration vers l'écran hôte (#3056), `null` tant qu'aucun ne l'a branché.
    private BiConsumer<String, ResteDeRestauration> compteRendu;

    /// Pont **optionnel** vers le(s) sélecteur(s) de colonnes de la vue (#994) : quand il est fourni,
    /// enregistrer une vue capture **aussi** la disposition des colonnes, et la rejouer la restaure. `null`
    /// pour une vue sans colonnes gérées (le comportement se réduit alors aux filtres, comme avant #994).
    private final AdaptateurColonnes adaptateurColonnes;

    /// Vues **par défaut** de l'écran (fournies par la vue hôte) : rendues **en premier**, en **lecture seule**
    /// (pas de renommer/supprimer). Repérées par un `id` `null` (jamais persistées). Sur une vue par défaut,
    /// « 💾 » enregistre les filtres courants **comme une nouvelle vue** (on ne peut pas l'écraser).
    private final List<VueSauvegardee> vuesParDefaut;

    /// Vue actuellement **active** (onglet surligné), ou `null` (aucune vue rejouée / filtres libres).
    private VueSauvegardee active;

    /// `true` si les filtres courants ont **divergé** de l'instantané de la vue active (modifications non
    /// enregistrées) : l'onglet actif propose alors « 💾 Enregistrer dans la vue ». Toujours faux sans vue
    /// active.
    private boolean modifiee;

    /// Garde : vrai le temps de [#appliquer(VueSauvegardee)], pour **ignorer** les changements de filtres émis
    /// par la restauration elle-même (sinon la vue tout juste rejouée serait aussitôt marquée « modifiée »).
    private boolean enApplication;

    /// Construit la barre d'onglets et la peuple immédiatement (vues persistées de la `feature`).
    ///
    /// @param onglets conteneur des onglets (fourni par la vue, typiquement un `HBox`/`FlowPane`)
    /// @param filtres barre de filtres pilotée (source de `decrire` / cible de `restaurer`)
    /// @param depot dépôt de persistance des vues mémorisées
    /// @param feature clé de l'écran/table (chaque écran ne voit que ses vues)
    /// @param saisieNom demande un nom à l'utilisateur (défaut fourni en argument) ; vide = annulation
    public GestionnaireVues(
            Pane onglets,
            GestionnaireFiltres<T> filtres,
            DepotVues depot,
            String feature,
            Function<String, Optional<String>> saisieNom) {
        this(onglets, filtres, depot, feature, List.of(), saisieNom);
    }

    /// Variante avec des **vues par défaut** en lecture seule (rendues en premier). Au chargement, la vue
    /// (par défaut ou utilisateur) correspondant aux filtres courants est activée ; à défaut, la **première
    /// vue par défaut** est appliquée : il y a donc **toujours** une vue active tant qu'il en existe une par
    /// défaut, ce qui évite d'avoir à sélectionner un onglet avant de modifier les filtres.
    public GestionnaireVues(
            Pane onglets,
            GestionnaireFiltres<T> filtres,
            DepotVues depot,
            String feature,
            List<VueSauvegardee> vuesParDefaut,
            Function<String, Optional<String>> saisieNom) {
        this(onglets, filtres, depot, feature, vuesParDefaut, null, saisieNom);
    }

    /// Variante **avec sélecteur de colonnes** (#994) : `adaptateurColonnes` (peut être `null`) permet aux
    /// vues d'enregistrer et de rejouer aussi la disposition des colonnes, en plus des filtres.
    public GestionnaireVues(
            Pane onglets,
            GestionnaireFiltres<T> filtres,
            DepotVues depot,
            String feature,
            List<VueSauvegardee> vuesParDefaut,
            AdaptateurColonnes adaptateurColonnes,
            Function<String, Optional<String>> saisieNom) {
        this.onglets = Objects.requireNonNull(onglets, "onglets");
        this.barre = new OngletsVues(
                onglets,
                new OngletsVues.Gestes(
                        this::appliquer,
                        vue -> saisieNom.apply(vue.nom()).ifPresent(nouveau -> renommer(vue, nouveau)),
                        this::supprimer,
                        this::enregistrerCommeNouvelle,
                        this::enregistrerDansActive));
        this.filtres = Objects.requireNonNull(filtres, "filtres");
        this.depot = Objects.requireNonNull(depot, "depot");
        this.feature = Objects.requireNonNull(feature, "feature");
        this.vuesParDefaut = List.copyOf(vuesParDefaut);
        this.adaptateurColonnes = adaptateurColonnes;
        this.saisieNom = Objects.requireNonNull(saisieNom, "saisieNom");
        this.filtres.surChangement(this::auChangementFiltres);
        rafraichir();
        activerAuChargement();
    }

    /// Réévalue si les filtres courants divergent de la vue active à chaque changement de filtre, et ne
    /// reconstruit la barre **qu'au basculement** de l'état (apparition/disparition du « 💾 »), pas à chaque
    /// frappe. Ignore les changements émis par [#appliquer(VueSauvegardee)] (garde [#enApplication]).
    private void auChangementFiltres() {
        if (enApplication) {
            return;
        }
        boolean neo = active != null && !filtres.decrire().equals(filtresDe(active));
        if (neo != modifiee) {
            modifiee = neo;
            rafraichir();
        }
    }

    /// L'état courant **complet** de la vue tel qu'il sera stocké : filtres + disposition des colonnes (une
    /// map vide si aucun [AdaptateurColonnes] n'est fourni).
    private DescripteurVue vueCourante() {
        Map<String, DescripteurColonnes> colonnes =
                adaptateurColonnes == null ? Map.of() : adaptateurColonnes.decrire();
        return new DescripteurVue(filtres.decrire(), colonnes);
    }

    /// Les **filtres** enregistrés dans `vue`, extraits du descripteur composite (format hérité #623 toléré).
    /// L'activité de la barre (badge « modifié », vue active au chargement) reste **pilotée par les filtres**.
    private static DescripteurFiltre filtresDe(VueSauvegardee vue) {
        return DescripteurVueJson.interpreter(vue.descripteurJson()).filtres();
    }

    /// Construit la barre d'onglets avec la **boîte de saisie standard** du nom de vue : un [TextInputDialog]
    /// ancré sur la fenêtre de `onglets`. Les écrans tabulaires (audio, analyse, multisite) partagent ce
    /// dialogue au lieu d'en dupliquer un chacun. Le constructeur à `saisieNom` explicite reste disponible
    /// pour les tests (qui pilotent le nommage sans ouvrir de modale).
    ///
    /// @param onglets conteneur des onglets (fournit aussi la fenêtre propriétaire du dialogue)
    /// @param filtres barre de filtres pilotée
    /// @param depot dépôt de persistance des vues
    /// @param feature clé de l'écran/table
    public static <T> GestionnaireVues<T> avecDialogue(
            Pane onglets, GestionnaireFiltres<T> filtres, DepotVues depot, String feature) {
        return avecDialogue(onglets, filtres, depot, feature, List.of());
    }

    /// Variante avec des **vues par défaut** (lecture seule, rendues en premier), avec la boîte de saisie
    /// standard.
    public static <T> GestionnaireVues<T> avecDialogue(
            Pane onglets,
            GestionnaireFiltres<T> filtres,
            DepotVues depot,
            String feature,
            List<VueSauvegardee> vuesParDefaut) {
        return new GestionnaireVues<>(
                onglets, filtres, depot, feature, vuesParDefaut, defaut -> demanderNom(onglets, defaut));
    }

    /// Variante avec **vues par défaut** et **sélecteur de colonnes** (#994) : les vues enregistrent et
    /// rejouent aussi la disposition des colonnes via `adaptateurColonnes`.
    public static <T> GestionnaireVues<T> avecDialogue(
            Pane onglets,
            GestionnaireFiltres<T> filtres,
            DepotVues depot,
            String feature,
            List<VueSauvegardee> vuesParDefaut,
            AdaptateurColonnes adaptateurColonnes) {
        return new GestionnaireVues<>(
                onglets,
                filtres,
                depot,
                feature,
                vuesParDefaut,
                adaptateurColonnes,
                defaut -> demanderNom(onglets, defaut));
    }

    /// Boîte de saisie standard du nom d'une vue (création ou renommage) : renvoie le nom nettoyé, ou vide
    /// si l'utilisateur annule ou laisse le champ blanc.
    private static Optional<String> demanderNom(Pane onglets, String defaut) {
        TextInputDialog dialogue = new TextInputDialog(defaut);
        dialogue.initOwner(onglets.getScene().getWindow());
        dialogue.setHeaderText("Nom de la vue");
        dialogue.setContentText("Nom :");
        return dialogue.showAndWait().map(String::trim).filter(nom -> !nom.isBlank());
    }

    /// Supprime `vue` (la désactive si c'était l'onglet actif) puis rafraîchit.
    public void supprimer(VueSauvegardee vue) {
        depot.delete(vue.id());
        if (vue.equals(active)) {
            active = null;
            modifiee = false;
        }
        rafraichir();
    }

    /// Recharge les vues de la `feature` et reconstruit la barre (un onglet par vue + le bouton « + Vue »).
    public void rafraichir() {
        barre.redessiner(toutesLesVues(), active, modifiee, GestionnaireVues::estParDefaut);
    }

    /// Enregistre les **filtres courants** (via `decrire`) comme une nouvelle vue nommée `nom`, l'active et
    /// rafraîchit la barre. Un nom vide/en blanc est refusé (retourne `null`).
    ///
    /// @return la vue insérée (avec son `id`), ou `null` si le nom est vide
    public VueSauvegardee enregistrer(String nom) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        VueSauvegardee vue = depot.insert(
                new VueSauvegardee(null, feature, nom.trim(), DescripteurVueJson.serialiser(vueCourante())));
        active = vue;
        modifiee = false; // la nouvelle vue capture exactement les filtres courants
        rafraichir();
        return vue;
    }

    /// Rejoue la combinaison de filtres de `vue` (la restaure sur la barre de filtres) et l'active. La garde
    /// [#enApplication] empêche que la restauration elle-même ne marque aussitôt la vue « modifiée ».
    public void appliquer(VueSauvegardee vue) {
        DescripteurVue descripteur = DescripteurVueJson.interpreter(vue.descripteurJson());
        enApplication = true;
        ResteDeRestauration reste = filtres.restaurer(descripteur.filtres());
        enApplication = false;
        rendreCompteDe(vue, reste);
        if (adaptateurColonnes != null && !descripteur.colonnes().isEmpty()) {
            adaptateurColonnes.restaurer(descripteur.colonnes());
        }
        active = vue;
        modifiee = false;
        rafraichir();
    }

    /// Dit à l'écran hôte qu'une vue s'est rejouée **amputée** (#3056, puis #3093 pour les critères).
    ///
    /// Le cas se produit quand les libellés offerts ont changé - « Z1 » est devenu « 640380 · Z1 »
    /// en #2995 - ou quand une valeur mémorisée est absente du jeu de lignes courant (une espèce
    /// qu'on n'a pas contactée cette fois-ci). Une vue peut aussi porter un critère que le catalogue
    /// de l'écran n'offre plus : [ResteDeRestauration] distingue les deux causes.
    ///
    /// La vue s'ouvre quand même : perdre l'accès à une vue enregistrée serait pire que le défaut.
    /// Mais elle **filtre moins** que lorsqu'elle a été enregistrée, et le taire laisserait croire le
    /// contraire. Bandeau et non modal : rejouer une vue est réversible et anodin (ADR 0023).
    private void rendreCompteDe(VueSauvegardee vue, ResteDeRestauration reste) {
        if (compteRendu == null || reste.estVide()) {
            return;
        }
        compteRendu.accept(vue.nom(), reste);
    }

    /// Branche le **compte rendu** de restauration sur le bandeau de l'écran hôte. Optionnel : sans
    /// lui, une vue amputée se rejoue en silence, ce qui était le comportement avant #3056.
    public GestionnaireVues<T> surRestauration(BiConsumer<String, ResteDeRestauration> compteRendu) {
        this.compteRendu = compteRendu;
        return this;
    }

    /// Au chargement : active la vue (par défaut ou utilisateur) dont l'instantané **correspond aux filtres
    /// courants** (sans les changer) ; à défaut, applique la **première vue par défaut** s'il en existe. Garantit
    /// une vue active dès l'ouverture (plus besoin de sélectionner un onglet avant de modifier).
    private void activerAuChargement() {
        DescripteurFiltre courant = filtres.decrire();
        Optional<VueSauvegardee> correspondante = toutesLesVues().stream()
                .filter(vue -> courant.equals(filtresDe(vue)))
                .findFirst();
        if (correspondante.isPresent()) {
            active = correspondante.get();
            rafraichir();
        } else if (!vuesParDefaut.isEmpty()) {
            appliquer(vuesParDefaut.get(0));
        }
    }

    /// Toutes les vues connues : **par défaut d'abord**, puis les persistées de la feature.
    private List<VueSauvegardee> toutesLesVues() {
        return Stream.concat(vuesParDefaut.stream(), depot.findByFeature(feature).stream())
                .toList();
    }

    /// Une vue est **par défaut** (lecture seule) si elle n'a pas d'`id` (jamais persistée).
    private static boolean estParDefaut(VueSauvegardee vue) {
        return vue.id() == null;
    }

    /// « 💾 » sur une vue **par défaut** (non écrasable) : enregistre les filtres courants comme une **nouvelle**
    /// vue nommée : même effet que « + Vue », mais accessible depuis l'onglet actif.
    private void enregistrerCommeNouvelle() {
        saisieNom.apply("").ifPresent(this::enregistrer);
    }

    /// Réécrit l'instantané de la **vue active** avec les filtres courants (« 💾 Enregistrer dans la vue ») :
    /// les modifications en cours deviennent le nouvel état enregistré de la vue. Sans effet si aucune vue
    /// n'est active.
    public void enregistrerDansActive() {
        if (active == null) {
            return;
        }
        VueSauvegardee misAJour =
                new VueSauvegardee(active.id(), feature, active.nom(), DescripteurVueJson.serialiser(vueCourante()));
        depot.update(misAJour);
        active = misAJour;
        modifiee = false;
        rafraichir();
    }

    /// Renomme `vue` (le descripteur est conservé) et rafraîchit. Un nom vide/en blanc est ignoré.
    public void renommer(VueSauvegardee vue, String nom) {
        if (nom != null && !nom.isBlank()) {
            VueSauvegardee renommee = new VueSauvegardee(vue.id(), vue.feature(), nom.trim(), vue.descripteurJson());
            depot.update(renommee);
            if (vue.equals(active)) {
                active = renommee;
            }
            rafraichir();
        }
    }
}
