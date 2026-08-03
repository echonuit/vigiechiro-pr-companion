package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.CritereListe;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.ValeursPresentes;
import fr.univ_amu.iut.commun.view.ValidationFormulaire;
import fr.univ_amu.iut.commun.view.VuesParDefaut;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/// Catalogue des **critères de filtrage** de la vue multi-sites (patron « à la Notion », #537 étape 6b).
/// Chaque critère est une puce ajoutable : **Carré** (n° de carré, champ texte), **Statut** de workflow,
/// **Verdict** de vérification, **Année** et **Analyse** (état du traitement serveur, #1338). La
/// **recherche texte** permanente ([#rechercheTexte()]) couvre carré, point, date et commune (#2791).
///
/// Pendant, côté multisite, du `CriteresAudio` / `CriteresAnalyse`. Les prédicats **réutilisent** la
/// sémantique de [FiltresMultisite#accepte(LignePassage)] : aucune logique de filtrage dupliquée. Aucune
/// présélection : ajouter une puce n'écarte rien tant qu'une valeur n'est pas saisie.
final class CriteresMultisite {

    /// Clé **stable** du critère Analyse (#1338), partagée par le critère et la vue « Résultats à importer ».
    private static final String ANALYSE = "analyse";

    /// Intitulé du critère Année, porté par la puce, l'invite du champ et son libellé accessible en
    /// saisie valide : les trois doivent dire la même chose.
    private static final String LIBELLE_ANNEE = "Année";

    private CriteresMultisite() {}

    /// Vues **par défaut** (lecture seule) du tableau des passages, rendues comme onglets avant les vues de
    /// l'utilisateur (#623), sur le modèle de `CriteresAudio` :
    /// - **« Tout »** (aucun filtre) : active au chargement, n'écarte rien ;
    /// - **« Résultats à importer »** (#1338) : les nuits dont l'analyse est terminée et dont les
    ///   observations ne sont **pas encore** en base : la réponse en un onglet à « lesquelles de mes nuits
    ///   sont prêtes ? », qui obligeait jusqu'ici à ouvrir chaque passage l'un après l'autre ;
    /// - **« Déposés »** (statut Déposé : nuits déjà envoyées) ;
    /// - **« Non vérifié »** (verdict Non vérifié : passages à contrôler) ;
    /// - **« À réactiver »** (statut Récupéré : rapatriée de Vigie-Chiro, il lui manque son audio).
    /// - **« Vérifiés »** (statut Vérifié).
    ///
    /// Chaque descripteur est sérialisé exactement comme [GestionnaireFiltres#decrire()] le produirait, pour
    /// que rejouer la vue laisse un état « non modifié ».
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut(
                        "Résultats à importer",
                        new DescripteurCritere(ANALYSE, List.of(EtatAnalyse.A_IMPORTER.name()))),
                vueParDefaut(
                        "Déposés",
                        new DescripteurCritere(ClesCriteres.STATUT_WORKFLOW, List.of(StatutWorkflow.DEPOSE.name()))),
                // #2581 : après une synchronisation, la question qui vient est « lesquelles attendent
                // encore leur audio ? ». Sans cette vue, il faudrait la reconstruire à la main à chaque
                // fois - et la liste des nuits récupérées est exactement celle des nuits à réactiver.
                vueParDefaut(
                        "À réactiver",
                        new DescripteurCritere(ClesCriteres.STATUT_WORKFLOW, List.of(StatutWorkflow.RECUPERE.name()))),
                vueParDefaut("Non vérifié", new DescripteurCritere("verdict", List.of(Verdict.A_VERIFIER.name()))),
                vueParDefaut(
                        "Vérifiés",
                        new DescripteurCritere(ClesCriteres.STATUT_WORKFLOW, List.of(StatutWorkflow.VERIFIE.name()))));
    }

    /// Critère **État d'analyse** (#1338) : liste déroulante, sans présélection. C'est lui qui porte la vue
    /// « Résultats à importer ».
    static CritereFiltre<LignePassage> analyse() {
        return CritereListe.enumeration(
                ANALYSE,
                "Analyse",
                "Choisir un état d'analyse",
                List.of(EtatAnalyse.values()),
                CriteresMultisite::libelleEtat,
                etat -> FiltresMultisite.parEtatAnalyse(etat)::accepte);
    }

    /// Libellé de l'état dans la liste déroulante. [EtatAnalyse#SANS_OBJET] n'a pas de libellé de badge
    /// (la cellule reste vide dans le tableau) : dans un menu, il lui en faut un, sans quoi l'entrée serait
    /// une ligne blanche que personne ne peut choisir sciemment.
    private static String libelleEtat(EtatAnalyse etat) {
        return etat == EtatAnalyse.SANS_OBJET ? "Nuit non déposée" : etat.libelle();
    }

    /// Une vue par défaut de cet écran : délégation à la fabrique partagée [VuesParDefaut] (#1257).
    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        return VuesParDefaut.vue("multisite", nom, criteres);
    }

    /// Critère **Carré** : champ texte du n° de carré (ex. `640380`). Éditable au clavier **et** posé par la
    /// carte (clic d'un carré, via [fr.univ_amu.iut.commun.view.GestionnaireFiltres#poser(String, List)]).
    /// Critère **Lieu** (#2968, chantier #2790) : liste à cocher des lieux **présents dans les passages
    /// filtrés**, toutes dimensions confondues, dans l'ordre commune, carré, point. Une ligne passe si
    /// **l'une** de ses dimensions figure parmi les valeurs cochées ([CritereListe#multipleParmi]) ; rien
    /// de coché n'écarte rien.
    ///
    /// **Trois dimensions, pas quatre** : [LignePassage] ne porte pas le nom de site. C'est un écart avec
    /// la vue audio (#2794), qui en a quatre, et il tient à la projection, pas à un choix d'ergonomie.
    ///
    /// **Le critère « Carré » subsiste à côté**, et ce n'est pas un doublon oublié. La raison invoquée
    /// jusqu'ici était la migration des vues mémorisées ; elle était vraie mais secondaire, et l'audit des
    /// quatre barres de filtres (#2967) en a exhumé une plus forte, qui tient même quand les vues stockées
    /// ne comptent pas.
    ///
    /// **« Carré » est la cible du clic sur la carte**, et c'est le seul critère de cet écran capable de
    /// recevoir une valeur **arbitraire**. La carte affiche l'agrégat **non filtré** des sites de
    /// l'utilisateur, passages ou non ; « Lieu » ne propose que les lieux **présents dans les passages
    /// filtrés**. Router le clic vers « Lieu » marcherait pour un carré qui a des passages, et **ne ferait
    /// rien du tout** pour un carré qui n'en a pas : [CritereListe#multipleParmi] ne coche que ce que sa
    /// liste contient. Un clic sans effet est pire que le filtre à zéro ligne d'aujourd'hui, qui apprend
    /// au moins que ce carré n'a rien.
    ///
    /// Le champ texte n'est donc pas une redondance à résorber : c'est la porte d'entrée d'une valeur qui
    /// vient d'ailleurs que d'une liste.
    static CritereFiltre<LignePassage> lieu(Supplier<? extends List<LignePassage>> passagesFiltres) {
        return CritereListe.multipleParmi(
                ClesCriteres.LIEU,
                "Lieu",
                "Choisir un lieu",
                () -> lieuxPresents(passagesFiltres.get()),
                CriteresMultisite::dimensionsLieu);
    }

    /// Lieux présents dans `passages` : les valeurs **distinctes** de chaque dimension, groupées par
    /// dimension (communes, puis carrés, puis points) et triées au sein de chacune. Un même libellé porté
    /// par deux dimensions n'apparaît qu'une fois : le coche vaut alors pour les deux.
    private static List<CritereListe.GroupeValeurs> lieuxPresents(List<LignePassage> passages) {
        return List.of(
                new CritereListe.GroupeValeurs("Communes", ValeursPresentes.de(passages, LignePassage::commune)),
                new CritereListe.GroupeValeurs("Carrés", ValeursPresentes.de(passages, LignePassage::numeroCarre)),
                new CritereListe.GroupeValeurs(
                        "Points", ValeursPresentes.de(passages, CriteresMultisite::pointQualifie)));
    }

    /// Le point **qualifié par son carré**, « 640380 · A1 » (#2992). Le schéma pose `UNIQUE(site_id, code)` :
    /// un code de point est unique **par site**, pas globalement, si bien que « A1 » désigne autant de
    /// lieux qu'il y a de carrés. Cet écran couvrant la saison entière, une entrée « A1 » y confondait
    /// silencieusement les A1 de tous les carrés. Qualifiée, chaque entrée désigne **un** lieu.
    private static String pointQualifie(LignePassage ligne) {
        return ligne.codePoint() == null ? null : ligne.numeroCarre() + " · " + ligne.codePoint();
    }

    /// Les dimensions de lieu d'un passage, valeurs nulles écartées.
    private static List<String> dimensionsLieu(LignePassage ligne) {
        return Stream.of(ligne.commune(), ligne.numeroCarre(), pointQualifie(ligne))
                .filter(Objects::nonNull)
                .toList();
    }

    static CritereFiltre<LignePassage> carre() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return "carre";
            }

            @Override
            public String libelle() {
                return "Carré";
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                TextField champ = new TextField();
                champ.setPromptText("N° carré");
                champ.textProperty().addListener((obs, avant, texte) -> applique.accept(predicatCarre(texte)));
                applique.accept(tout()); // pas de valeur → aucun filtre
                return champ;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                String carre = texteOuNull(((TextField) editeur).getText());
                return carre == null ? List.of() : List.of(carre);
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                ((TextField) editeur).setText(valeurs.isEmpty() ? "" : valeurs.get(0));
                return List.of(); // champ libre : une valeur mémorisée s'y replace toujours
            }
        };
    }

    /// Critère **Campagne** (#2355) : champ libre, correspondance **partielle** sur le nom (on tape
    /// « ENS », pas le libellé exact), même ergonomie que le carré, et pour la même raison : la liste des
    /// campagnes n'est pas connue de la vue, et taper est plus rapide que dérouler.
    static CritereFiltre<LignePassage> campagne() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return "campagne";
            }

            @Override
            public String libelle() {
                return "Campagne";
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                TextField champ = new TextField();
                champ.setPromptText("Nom de campagne");
                champ.textProperty().addListener((obs, avant, texte) -> applique.accept(predicatCampagne(texte)));
                applique.accept(tout()); // pas de valeur → aucun filtre
                return champ;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                String campagne = texteOuNull(((TextField) editeur).getText());
                return campagne == null ? List.of() : List.of(campagne);
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                ((TextField) editeur).setText(valeurs.isEmpty() ? "" : valeurs.get(0));
                return List.of(); // champ libre : une valeur mémorisée s'y replace toujours
            }
        };
    }

    /// Critère **Statut de workflow** : liste déroulante, sans présélection.
    static CritereFiltre<LignePassage> statut() {
        // Renomme de « statut » en « statut_workflow » (#3096) : cette cle designait le statut
        // d OBSERVATION sur deux autres ecrans, et le mecanisme de transport (#476) arme exactement
        // ce piege. Les vues enregistrees sous l ancien nom continuent de se rejouer, via
        // `nomsHerites`, sans migration de base.
        CritereFiltre<LignePassage> critere = CritereListe.enumeration(
                ClesCriteres.STATUT_WORKFLOW,
                "Statut",
                "Choisir un statut",
                List.of(StatutWorkflow.values()),
                StatutWorkflow::libelle,
                statut -> FiltresMultisite.parStatut(statut)::accepte);
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return critere.nom();
            }

            @Override
            public List<String> nomsHerites() {
                return List.of(ClesCriteres.STATUT_OBSERVATION);
            }

            @Override
            public String libelle() {
                return critere.libelle();
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                return critere.editeur(applique);
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                return critere.valeurCourante(editeur);
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                return critere.restaurerValeurs(editeur, valeurs);
            }
        };
    }

    /// Critère **Verdict de vérification** : liste déroulante, sans présélection.
    static CritereFiltre<LignePassage> verdict() {
        return CritereListe.enumeration(
                "verdict",
                "Verdict",
                "Choisir un verdict",
                List.of(Verdict.values()),
                Verdict::libelle,
                verdict -> FiltresMultisite.parVerdict(verdict)::accepte);
    }

    /// Critère **Année** : champ texte numérique. Une saisie **illisible** ne filtre pas, et le **dit**
    /// (#3094).
    ///
    /// Le comportement de filtrage est inchangé : `202O` (avec un O) n'écarte rien, comme avant. Ce qui
    /// change est qu'il cesse de le taire. Une puce posée, remplie, d'apparence active, sur une table
    /// qui n'est pas filtrée, est le genre d'état qui coûte le plus cher en confiance : ce n'est pas une
    /// erreur visible qu'on corrige, c'est un résultat qu'on croit filtré et qui ne l'est pas.
    ///
    /// La condition est « **saisi mais illisible** », comme le recommande la Javadoc de
    /// [ValidationFormulaire#marquerInvalide] : un champ encore vide reste neutre et ne rougit pas avant
    /// toute saisie. C'est la même règle que le socle applique aux puces à liste, où rien de coché
    /// n'écarte rien.
    static CritereFiltre<LignePassage> annee() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return "annee";
            }

            @Override
            public String libelle() {
                return LIBELLE_ANNEE;
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                TextField champ = new TextField();
                champ.setPromptText(LIBELLE_ANNEE);
                signalerSaisieIllisible(champ);
                champ.textProperty().addListener((obs, avant, texte) -> applique.accept(predicatAnnee(texte)));
                applique.accept(tout());
                return champ;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Integer annee = anneeOuNull(((TextField) editeur).getText());
                return annee == null ? List.of() : List.of(annee.toString());
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                ((TextField) editeur).setText(valeurs.isEmpty() ? "" : valeurs.get(0));
                return List.of(); // champ libre : une valeur mémorisée s'y replace toujours
            }
        };
    }

    /// **Recherche texte** de la barre : vrai si le n° de carré, le code du point ou la date d'une ligne
    /// contient l'aiguille (insensible casse/accents). Fournie au `GestionnaireFiltres` (champ permanent).
    static BiPredicate<LignePassage, String> rechercheTexte() {
        return CriteresMultisite::correspond;
    }

    private static boolean correspond(LignePassage ligne, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(ligne.numeroCarre(), aiguille)
                || contient(ligne.codePoint(), aiguille)
                || contient(ligne.dateEnregistrement(), aiguille)
                || contient(ligne.commune(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    private static Predicate<LignePassage> predicatCarre(String texte) {
        String carre = texteOuNull(texte);
        return carre == null ? tout() : FiltresMultisite.parSite(carre)::accepte;
    }

    private static Predicate<LignePassage> predicatCampagne(String texte) {
        String campagne = texteOuNull(texte);
        return campagne == null ? tout() : FiltresMultisite.parCampagne(campagne)::accepte;
    }

    private static Predicate<LignePassage> predicatAnnee(String texte) {
        Integer annee = anneeOuNull(texte);
        return annee == null ? tout() : FiltresMultisite.parAnnee(annee)::accepte;
    }

    /// Texte accessible du champ Année quand la saisie est illisible (#3094).
    ///
    /// La bordure rouge est un signal **de couleur seule**, et #2119 relève déjà que les explications
    /// réservées à la souris n'atteignent pas tout le monde. Le champ porte donc aussi ce que le rouge
    /// veut dire, sous une forme qu'un lecteur d'écran restitue.
    private static final String AIDE_ANNEE_ILLISIBLE = "Année illisible : saisissez quatre chiffres, par exemple 2026.";

    /// Marque le champ Année quand il est **rempli mais illisible**, et le laisse neutre tant qu'il est
    /// vide. Le libellé accessible suit la même condition, pour que le signal ne soit pas qu'une couleur.
    private static void signalerSaisieIllisible(TextField champ) {
        BooleanBinding illisible = Bindings.createBooleanBinding(
                () -> texteOuNull(champ.getText()) != null && anneeOuNull(champ.getText()) == null,
                champ.textProperty());
        ValidationFormulaire.marquerInvalide(champ, illisible);
        champ.accessibleTextProperty()
                .bind(Bindings.when(illisible).then(AIDE_ANNEE_ILLISIBLE).otherwise(LIBELLE_ANNEE));
    }

    /// Prédicat neutre (aucun filtre) : la puce est présente mais ne restreint rien tant qu'aucune valeur
    /// n'est saisie (contrat [CritereFiltre#editeur] : appliquer un prédicat **non nul**).
    private static Predicate<LignePassage> tout() {
        return ligne -> true;
    }

    private static String texteOuNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    /// Année saisie, ou `null` si vide/non numérique.
    private static Integer anneeOuNull(String texte) {
        String annee = texteOuNull(texte);
        if (annee == null) {
            return null;
        }
        try {
            return Integer.valueOf(annee);
        } catch (NumberFormatException saisieNonNumerique) {
            return null;
        }
    }
}
