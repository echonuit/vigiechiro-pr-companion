package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.CritereBooleen;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.CritereLieu;
import fr.univ_amu.iut.commun.view.CritereListe;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.ValeursPresentes;
import fr.univ_amu.iut.commun.view.VuesParDefaut;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Catalogue des **critères de filtrage** de l'inventaire analyse (patron « à la Notion », #537). Chaque
/// critère est une entrée du menu « + Filtre » qui s'ajoute comme puce : **Statut** de revue et **Taxon
/// parent** (groupe, #518). La **recherche texte** permanente (espèce, carré) est fournie à part
/// ([#rechercheTexte()]).
///
/// Pendant, côté analyse, du `CriteresAudio` : de simples `CritereFiltre<ObservationAnalyse>` dont l'éditeur
/// pilote le prédicat du socle [fr.univ_amu.iut.commun.viewmodel.Filtres]. Aucune présélection sur les deux
/// puces : ajouter la puce n'écarte rien tant qu'une valeur n'est pas choisie (l'inventaire reste complet
/// par défaut, comme avant la barre à puces).
final class CriteresAnalyse {

    /// Catégories du référentiel qui ne sont pas des chiroptères : la même liste que sur l'écran Activité
    /// de la nuit, les deux vues partageant la matière (#2615).
    private static final List<String> HORS_CHIROPTERES =
            List.of("Orthoptères et cigales", "Autres mammifères", "Oiseaux", "Autres invertébrés", "Amphibiens");

    private CriteresAnalyse() {}

    /// Vues **par défaut** (lecture seule) de l'inventaire analyse, rendues comme onglets avant les vues de
    /// l'utilisateur (#623), sur le modèle de `CriteresAudio` :
    /// - **« Tout »** (aucun filtre) : active au chargement, n'écarte rien ;
    /// - **« À valider »** (statut À revoir, le cœur de la revue) ;
    /// - **« Validées »** (statut Validée : ce qui est déjà traité) ;
    /// - **« Chiroptères »** (groupe Chiroptères, #471).
    ///
    /// Chaque descripteur est sérialisé exactement comme [GestionnaireFiltres#decrire()] le produirait, pour
    /// que rejouer la vue laisse un état « non modifié ».
    /// Onglets **par défaut** : les deux angles de revue (à valider, validées) et la partition par
    /// **catégorie du référentiel**, alignée sur l'écran Activité de la nuit. Tadarida ne détecte pas
    /// que des chauves-souris : sans ces onglets, orthoptères et micromammifères s'intercalent dans
    /// l'inventaire au même rang que les chiroptères.
    ///
    /// « Autres » cumule toutes les catégories non-chiroptères ([#HORS_CHIROPTERES]) depuis #2615 : les
    /// deux écrans offrent donc la même partition, un onglet ne portant plus le nom d'une seule catégorie
    /// pour en désigner plusieurs.
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut(
                        "À valider",
                        new DescripteurCritere(
                                ClesCriteres.STATUT_OBSERVATION, List.of(StatutObservation.NON_TOUCHEE.name()))),
                vueParDefaut(
                        "Validées",
                        new DescripteurCritere(
                                ClesCriteres.STATUT_OBSERVATION, List.of(StatutObservation.VALIDEE.name()))),
                vueParDefaut("Chiroptères", new DescripteurCritere(ClesCriteres.GROUPE, List.of("Chiroptères"))),
                vueParDefaut("Autres", new DescripteurCritere(ClesCriteres.GROUPE, HORS_CHIROPTERES)),
                vueParDefaut("Espèces prioritaires", new DescripteurCritere(ClesCriteres.A_ENJEU, List.of())));
    }

    /// Une vue par défaut de cet écran : délégation à la fabrique partagée [VuesParDefaut] (#1257).
    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        return VuesParDefaut.vue("analyse", nom, criteres);
    }

    /// Critère **Statut de revue** : éditeur = liste déroulante (Non touchée / Validée / Corrigée…) dans la
    /// puce, **sans présélection** (aucun filtre tant qu'un statut n'est pas choisi).
    static CritereFiltre<ObservationAnalyse> statut() {
        return CritereListe.enumeration(
                ClesCriteres.STATUT_OBSERVATION,
                "Statut",
                "Choisir un statut",
                List.of(StatutObservation.values()),
                FormatAnalyse::libelleStatut,
                statut -> observation -> observation.statut() == statut);
    }

    /// Critère **Taxon parent** (groupe, #518) : éditeur à **choix multiple** sur les groupes présents
    /// dans l'inventaire, sans présélection. Le multiple sert l'onglet « Autres », qui cumule les
    /// catégories non-chiroptères (#2615).
    /// Critère **Nature de la nuit** (#2614) : protocole ou participation opportuniste (#2525). Même
    /// dimension et mêmes libellés que sur l'écran Activité de la nuit : une nuit opportuniste ne compte
    /// pas de la même façon, et se mêlait jusqu'ici sans le dire aux nuits du protocole.
    /// Critère **Espèces à enjeu** (#2353) : garde les observations dont le taxon retenu est une espèce
    /// **prioritaire** au sens du Plan National d'Actions Chiroptères. Critère **sans éditeur** (booléen) :
    /// la présence de la puce active le filtre, comme sur l'écran de revue.
    ///
    /// Porte l'onglet « Espèces prioritaires » ([#vuesParDefaut()]) : c'est l'information qu'un naturaliste
    /// cherche en premier, et l'inventaire d'une saison la noyait parmi des dizaines d'espèces.
    static CritereFiltre<ObservationAnalyse> aEnjeu(Predicate<ObservationAnalyse> estPrioritaire) {
        return CritereBooleen.de(ClesCriteres.A_ENJEU, "Espèces à enjeu", estPrioritaire);
    }

    static CritereFiltre<ObservationAnalyse> natureNuit(Supplier<Set<Long>> opportunistes) {
        return CritereListe.simple(
                "natureNuit",
                "Nature de la nuit",
                "Protocole ou opportuniste",
                () -> NatureNuit.VALEURS,
                observation -> NatureNuit.de(observation.idPassage(), opportunistes.get()));
    }

    /// Les taxons parents presents dans `observations`, distincts et tries : le domaine du critere
    /// « Taxon parent » quand il est cascade sur les lignes que les autres criteres laissent
    /// passer (#3095).
    static List<String> groupesDe(List<ObservationAnalyse> observations) {
        return ValeursPresentes.de(observations, ObservationAnalyse::groupe);
    }

    static CritereFiltre<ObservationAnalyse> groupe(Supplier<? extends List<String>> groupesPresents) {
        return CritereListe.multiple(
                ClesCriteres.GROUPE,
                "Taxon parent",
                "Choisir un taxon parent",
                groupesPresents,
                ObservationAnalyse::groupe);
    }

    /// Critère **Lieu** (#2966, chantier #2790) : liste à cocher des lieux **présents dans les
    /// observations filtrées**, dans l'ordre commune, carré, point. Une observation passe si **l'une**
    /// de ses dimensions figure parmi les valeurs cochées ([CritereListe#multipleParmi]) ; rien de coché
    /// n'écarte rien.
    ///
    /// C'est la jumelle de `CriteresAudio.lieu` (#2794), dont elle reprend le libellé et l'ordre des
    /// dimensions à dessein : le vocabulaire d'un critère se lit d'un écran à l'autre, et deux ordres
    /// différents pour la même puce se paieraient à chaque va-et-vient.
    ///
    /// Le carré porte son **nom convivial** quand il en a un (« 640380 · Vallon », #3157). Le groupe
    /// « Sites » qui doublait le groupe « Carrés » a disparu : les deux désignaient le même objet.
    ///
    /// Le **point** a manqué ici jusqu'à #3161, et l'écran s'en trouvait contredit : sa table
    /// Observations affichait un code de point sur lequel on ne pouvait pas filtrer. La cause n'était
    /// pas ergonomique mais tenait à une colonne non remontée, que #3160 a corrigée.
    static CritereFiltre<ObservationAnalyse> lieu(Supplier<? extends List<ObservationAnalyse>> observationsFiltrees) {
        return CritereLieu.de(
                observationsFiltrees::get,
                List.of(
                        new CritereLieu.Dimension<>("Communes", ObservationAnalyse::commune),
                        CritereLieu.carres(ObservationAnalyse::numeroCarre, ObservationAnalyse::nomSite),
                        CritereLieu.points(CriteresAnalyse::pointQualifie)));
    }

    /// Le point **qualifié par son carré**, « 640380 · A1 » (#2992) : le schéma pose
    /// `UNIQUE(site_id, code)`, donc un code seul désigne autant de lieux qu'il y a de carrés. Cet écran
    /// couvre l'inventaire entier de l'utilisateur, où le cas est la règle plutôt que l'exception.
    private static String pointQualifie(ObservationAnalyse observation) {
        return observation.codePoint() == null
                ? null
                : LieuQualifie.qualifier(observation.numeroCarre(), observation.codePoint());
    }

    /// **Recherche texte** de la barre : vrai si un des champs cherchables d'une observation (taxon retenu,
    /// nom vernaculaire, nom latin, n° de carré, nom de site) contient l'aiguille (insensible casse/accents).
    /// Fournie au [fr.univ_amu.iut.commun.view.GestionnaireFiltres], qui l'applique au champ permanent.
    static BiPredicate<ObservationAnalyse, String> rechercheTexte() {
        return CriteresAnalyse::correspond;
    }

    private static boolean correspond(ObservationAnalyse observation, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(observation.taxonRetenu(), aiguille)
                || contient(observation.nomVernaculaireFr(), aiguille)
                || contient(observation.nomLatin(), aiguille)
                || contient(observation.numeroCarre(), aiguille)
                || contient(observation.nomSite(), aiguille)
                || contient(observation.commune(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }
}
