package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.CritereListe;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.VuesParDefaut;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/// Catalogue des **critères de filtrage** de la vue Activité (patron « à la Notion », socle
/// [fr.univ_amu.iut.commun.viewmodel.Filtres]), pendant du [CriteresAnalyse] mais sur [ContactHoraire].
/// Quatre puces cascadables — **Carré**, **Point**, **Nuit** et **Taxon parent** (groupe) —, la **Nature
/// de la nuit** (protocole ou opportuniste), plus la recherche texte permanente.
///
/// La sélection d'**espèce** n'est **pas** un filtre ici : elle vit dans les cases à cocher de la courbe
/// (quelles courbes tracer), pas dans le sous-ensemble de données. Aucune présélection : ajouter une puce
/// n'écarte rien tant qu'une valeur n'est pas choisie.
final class CriteresActivite {

    /// Clé du critère « Taxon parent », partagée par le filtre et les onglets par défaut.
    private static final String GROUPE = "groupe";

    /// Les catégories du référentiel qui ne sont **pas** des chiroptères. Énumérées, et non exprimées par
    /// une négation : le filtre dit alors ce qu'il **retient**, ce qui se lit dans la puce et se rejoue
    /// dans une vue mémorisée. Contrepartie assumée — une catégorie ajoutée un jour au référentiel devra
    /// être inscrite ici (#2615).
    private static final List<String> HORS_CHIROPTERES =
            List.of("Orthoptères et cigales", "Autres mammifères", "Oiseaux", "Autres invertébrés", "Amphibiens");

    private CriteresActivite() {}

    /// Critère **Carré** : liste déroulante des carrés présents (fournis par `carresPresents`, lus à l'ajout
    /// de la puce), sans présélection. Tête de la cascade carré → point → nuit.
    static CritereFiltre<ContactHoraire> carre(Supplier<? extends List<String>> carresPresents) {
        return CritereListe.simple("carre", "Carré", "Choisir un carré", carresPresents, ContactHoraire::numeroCarre);
    }

    /// Critère **Point** d'écoute : liste déroulante des points présents, sans présélection.
    static CritereFiltre<ContactHoraire> point(Supplier<? extends List<String>> pointsPresents) {
        return CritereListe.simple("point", "Point", "Choisir un point", pointsPresents, ContactHoraire::codePoint);
    }

    /// Critère **Nuit** (une nuit = un passage) : liste déroulante des nuits présentes (dates du soir),
    /// sans présélection.
    static CritereFiltre<ContactHoraire> nuit(Supplier<? extends List<String>> nuitsPresentes) {
        return CritereListe.simple("nuit", "Nuit", "Choisir une nuit", nuitsPresentes, CriteresActivite::libelleNuit);
    }

    /// Critère **Taxon parent** (groupe) : liste déroulante des groupes présents, sans présélection.
    static CritereFiltre<ContactHoraire> groupe(Supplier<? extends List<String>> groupesPresents) {
        // Choix MULTIPLE sur cette dimension seule : c'est elle que l'onglet « Autres » cumule
        // (orthoptères, micromammifères, oiseaux…), là où un carré ou une nuit se choisissent un à un.
        return CritereListe.multiple(
                GROUPE, "Taxon parent", "Choisir un taxon parent", groupesPresents, ContactHoraire::groupe);
    }

    /// Critère **Nature de la nuit** (#2614) : protocole ou participation opportuniste (#2525), lu sur
    /// l'ensemble des passages marqués que fournit `opportunistes` — un ensemble déjà en mémoire, relu à
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

    private static String libelleNuit(ContactHoraire contact) {
        return contact.nuit() == null ? null : contact.nuit().toString();
    }

    /// Onglets **par défaut** de l'écran, rendus en lecture seule avant les vues de l'utilisateur : ils
    /// partitionnent les taxons détectés par **catégorie du référentiel** (niveau `Catégorie` de
    /// `taxonomic_group`, semé par V05).
    ///
    /// Tadarida ne détecte pas que des chauves-souris : sur une vraie saison, orthoptères, autres
    /// mammifères et oiseaux figurent au même rang que les chiroptères, et la présélection des cinq taxons
    /// les plus contactés peut retenir une sauterelle — tracée alors comme une espèce de chauve-souris.
    ///
    /// Trois onglets qui **partitionnent** : « Tout », « Chiroptères », et un « Autres » qui tient sa
    /// promesse depuis #2615 — il cumule toutes les catégories non-chiroptères ([#HORS_CHIROPTERES]),
    /// que le critère à choix multiple sait désormais retenir d'un seul filtre.
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut("Chiroptères", new DescripteurCritere(GROUPE, List.of("Chiroptères"))),
                vueParDefaut("Autres", new DescripteurCritere(GROUPE, HORS_CHIROPTERES)));
    }

    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        return VuesParDefaut.vue("activite", nom, criteres);
    }

    /// **Recherche texte** de la barre : vrai si un champ cherchable d'un contact (taxon retenu, nom
    /// vernaculaire, n° de carré, code point) contient l'aiguille (insensible casse/accents). Fournie au
    /// [fr.univ_amu.iut.commun.view.GestionnaireFiltres], qui l'applique au champ permanent.
    static BiPredicate<ContactHoraire, String> rechercheTexte() {
        return CriteresActivite::correspond;
    }

    private static boolean correspond(ContactHoraire contact, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(contact.taxon(), aiguille)
                || contient(contact.nomEspece(), aiguille)
                || contient(contact.numeroCarre(), aiguille)
                || contient(contact.codePoint(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }
}
