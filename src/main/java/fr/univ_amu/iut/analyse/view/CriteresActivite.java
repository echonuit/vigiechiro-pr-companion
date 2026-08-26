package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
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
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Catalogue des **critères de filtrage** de la vue Activité (patron « à la Notion », socle
/// [fr.univ_amu.iut.commun.viewmodel.Filtres]), pendant du [CriteresAnalyse] mais sur [ContactHoraire].
/// Trois puces (**Lieu**, **Nuit** et **Taxon parent** (groupe)), la **Nature de la nuit** (protocole ou
/// opportuniste), plus la recherche texte permanente.
///
/// La sélection d'**espèce** n'est **pas** un filtre ici : elle vit dans les cases à cocher de la courbe
/// (quelles courbes tracer), pas dans le sous-ensemble de données. Aucune présélection : ajouter une puce
/// n'écarte rien tant qu'une valeur n'est pas choisie.
final class CriteresActivite {

    /// Les catégories du référentiel qui ne sont **pas** des chiroptères. Énumérées, et non exprimées par
    /// une négation : le filtre dit alors ce qu'il **retient**, ce qui se lit dans la puce et se rejoue
    /// dans une vue mémorisée. Contrepartie assumée : une catégorie ajoutée un jour au référentiel devra
    /// être inscrite ici (#2615).
    private static final List<String> HORS_CHIROPTERES =
            List.of("Orthoptères et cigales", "Autres mammifères", "Oiseaux", "Autres invertébrés", "Amphibiens");

    private CriteresActivite() {}

    /// Critère **Lieu** (#2967) : liste à cocher des lieux présents dans les contacts filtrés, toutes
    /// dimensions confondues, dans l'ordre commune, carré, point. Un contact passe si **l'une** de ses
    /// dimensions figure parmi les valeurs cochées ([CritereListe#multipleParmi]) ; rien de coché n'écarte
    /// rien. Le carré porte son nom convivial quand il en a un (« 640380 · Vallon », #3157), depuis que la
    /// projection le remonte (#3175). C'est la jumelle de `CriteresAnalyse.lieu` et de `CriteresAudio.lieu`.
    ///
    /// Elle remplace un **Carré** et un **Point** à choix unique, sans commune. La conjonction de deux puces
    /// disait « le point A1 du carré 640380 » ; depuis que le point est qualifié
    /// ([ContactHoraire#pointQualifie], règle de #2992), « 640380 · A1 » désigne la même chose, et la puce
    /// sait en outre retenir deux carrés.
    static CritereFiltre<ContactHoraire> lieu(Supplier<? extends List<ContactHoraire>> contactsFiltres) {
        return CritereLieu.de(
                contactsFiltres::get,
                List.of(
                        new CritereLieu.Dimension<>("Communes", ContactHoraire::commune),
                        CritereLieu.carres(ContactHoraire::numeroCarre, ContactHoraire::nomSite),
                        CritereLieu.points(ContactHoraire::pointQualifie)));
    }

    /// Critère **Nuit** (une nuit = un passage) : liste déroulante des nuits présentes (dates du soir),
    /// sans présélection.
    static CritereFiltre<ContactHoraire> nuit(Supplier<? extends List<String>> nuitsPresentes) {
        return CritereListe.simple("nuit", "Nuit", "Choisir une nuit", nuitsPresentes, CriteresActivite::libelleNuit);
    }

    /// Critère **Taxon parent** (groupe) : liste déroulante des groupes présents, sans présélection.
    /// Les taxons parents presents dans `contacts`, distincts et tries : le domaine du critere
    /// « Taxon parent » quand il est cascade (#3095).
    static List<String> groupesDe(List<ContactHoraire> contacts) {
        return ValeursPresentes.de(contacts, ContactHoraire::groupe);
    }

    static CritereFiltre<ContactHoraire> groupe(Supplier<? extends List<String>> groupesPresents) {
        // Choix MULTIPLE sur cette dimension seule : c'est elle que l'onglet « Autres » cumule
        // (orthoptères, micromammifères, oiseaux…), là où un carré ou une nuit se choisissent un à un.
        return CritereListe.multiple(
                ClesCriteres.GROUPE,
                "Taxon parent",
                "Choisir un taxon parent",
                groupesPresents,
                ContactHoraire::groupe);
    }

    /// Critère **Nature de la nuit** (#2614) : protocole ou participation opportuniste (#2525), lu sur
    /// l'ensemble des passages marqués que fournit `opportunistes` : un ensemble déjà en mémoire, relu à
    /// chaque ligne sans coût. Sans présélection : la puce n'écarte rien tant qu'une nature n'est pas
    /// choisie.
    static CritereFiltre<ContactHoraire> natureNuit(Supplier<Set<Long>> opportunistes) {
        return CritereListe.simple(
                "natureNuit",
                "Nature de la nuit",
                "Protocole ou opportuniste",
                () -> NatureNuit.VALEURS,
                contact -> NatureNuit.de(contact.idPassage(), opportunistes.get()));
    }

    /// Critère **Espèces à enjeu** (#2353) : ne trace que les courbes des espèces **prioritaires** au sens
    /// du Plan National d'Actions Chiroptères. Critère **sans éditeur** (booléen) : la présence de la puce
    /// active le filtre.
    ///
    /// Porte l'onglet « Espèces prioritaires » ([#vuesParDefaut()]) : sur une nuit à plusieurs milliers de
    /// contacts, il répond d'un clic à « qu'ai-je entendu qui compte ? ».
    static CritereFiltre<ContactHoraire> aEnjeu(Predicate<ContactHoraire> estPrioritaire) {
        return CritereBooleen.de(ClesCriteres.A_ENJEU, "Espèces à enjeu", estPrioritaire);
    }

    private static String libelleNuit(ContactHoraire contact) {
        return contact.nuit() == null ? null : contact.nuit().toString();
    }

    /// Onglets **par défaut** de l'écran, rendus en lecture seule avant les vues de l'utilisateur : ils
    /// partitionnent les taxons détectés par **catégorie du référentiel** (niveau `Catégorie` de
    /// `taxonomic_group`, semé par V05).
    ///
    /// Tadarida ne détecte pas que des chauves-souris : sur une vraie saison, orthoptères, autres
    /// mammifères et oiseaux figurent au même rang que les chiroptères, et la présélection des cinq taxons
    /// les plus contactés peut retenir une sauterelle : tracée alors comme une espèce de chauve-souris.
    ///
    /// Trois onglets qui **partitionnent** : « Tout », « Chiroptères », et un « Autres » qui tient sa
    /// promesse depuis #2615 : il cumule toutes les catégories non-chiroptères ([#HORS_CHIROPTERES]),
    /// que le critère à choix multiple sait désormais retenir d'un seul filtre.
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueChiropteres(),
                vueParDefaut("Autres", new DescripteurCritere(ClesCriteres.GROUPE, HORS_CHIROPTERES)),
                vueParDefaut("Espèces prioritaires", new DescripteurCritere(ClesCriteres.A_ENJEU, List.of())));
    }

    /// La vue sur laquelle l'écran **s'ouvre** (#2616) : « Chiroptères ».
    ///
    /// Sans elle, l'écran s'ouvrait sur « Tout », et la présélection des cinq taxons les plus contactés
    /// pouvait retenir une sauterelle : tracée alors, sur le même graphe et avec la même allure, qu'une
    /// espèce de chauve-souris. Le protocole ne vise que les chiroptères : c'est donc eux que l'écran
    /// montre d'abord, les autres catégories restant à un onglet de distance.
    ///
    /// Elle **n'est pas** un filtre imposé : l'onglet actif le dit, et « Tout » est juste à côté.
    static VueSauvegardee vueDOuverture() {
        return vueChiropteres();
    }

    private static VueSauvegardee vueChiropteres() {
        return vueParDefaut("Chiroptères", new DescripteurCritere(ClesCriteres.GROUPE, List.of("Chiroptères")));
    }

    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        return VuesParDefaut.vue("activite", nom, criteres);
    }

    /// **Recherche texte** de la barre : vrai si un champ cherchable d'un contact (taxon retenu, nom
    /// vernaculaire, commune, n° de carré, code point) contient l'aiguille (insensible casse/accents). Fournie au
    /// [fr.univ_amu.iut.commun.view.GestionnaireFiltres], qui l'applique au champ permanent.
    static BiPredicate<ContactHoraire, String> rechercheTexte() {
        return CriteresActivite::correspond;
    }

    private static boolean correspond(ContactHoraire contact, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return NormalisationTexte.contient(contact.taxon(), aiguille)
                || NormalisationTexte.contient(contact.nomEspece(), aiguille)
                || NormalisationTexte.contient(contact.commune(), aiguille)
                || NormalisationTexte.contient(contact.numeroCarre(), aiguille)
                || NormalisationTexte.contient(contact.codePoint(), aiguille)
                || NormalisationTexte.contient(contact.nomSite(), aiguille);
    }
}
