package fr.univ_amu.iut.audit.view;

import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.CritereListe;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/// Catalogue des **critères de filtrage** de l'Audit de cohérence (#3100), cinquième écran posé sur le
/// socle « à la Notion ».
///
/// Les critères se lisent directement du record : `ConstatAudit(severite, categorie, idPassage, cible,
/// detail)`. Les deux premières dimensions sont des énumérations, la troisième un passage, et les deux
/// dernières du texte libre - donc la recherche.
///
/// ## Des clés propres à cet écran
///
/// `gravite`, `categorie` et `passage` ne sont partagées avec aucun autre écran : elles restent donc
/// ici, conformément au contrat de [fr.univ_amu.iut.commun.view.ClesCriteres] (#3096), qui ne porte que
/// les clés réellement communes.
///
/// ⚠️ `categorie` n'a rien à voir avec le taxon parent des autres écrans (`groupe`) : ce sont deux
/// concepts distincts, et c'est précisément pour éviter ce genre de collision que les clés partagées
/// sont déclarées à un seul endroit.
final class CriteresAudit {

    /// Clé du critère **Gravité**.
    static final String GRAVITE = "gravite";

    /// Clé du critère **Catégorie** de constat.
    static final String CATEGORIE = "categorie";

    /// Clé du critère **Passage**.
    static final String PASSAGE = "passage";

    private CriteresAudit() {}

    /// Critère **Gravité** : liste déroulante des sévérités, sans présélection.
    ///
    /// Pas de présélection, malgré la tentation d'ouvrir sur les erreurs : ce serait une entorse à la
    /// règle du socle (« une puce ajoutée n'écarte rien »), et l'ADR 3099 pose que les deux entorses
    /// existantes le sont parce que le geste de revue le justifie. Ici, un audit se lit d'abord en
    /// entier.
    static CritereFiltre<ConstatAudit> gravite() {
        return CritereListe.enumeration(
                GRAVITE,
                "Gravité",
                "Choisir une gravité",
                List.of(Severite.values()),
                Severite::libelle,
                severite -> constat -> constat.severite() == severite);
    }

    /// Critère **Catégorie** : liste déroulante des natures de constat, sans présélection.
    static CritereFiltre<ConstatAudit> categorie() {
        return CritereListe.enumeration(
                CATEGORIE,
                "Catégorie",
                "Choisir une catégorie",
                List.of(CategorieConstat.values()),
                CategorieConstat::libelle,
                categorie -> constat -> constat.categorie() == categorie);
    }

    /// Critère **Passage** : liste déroulante des passages **présents dans les constats courants**.
    ///
    /// C'est la question la plus fréquente devant un audit de saison : « qu'est-ce qui cloche sur cette
    /// nuit-là ? ».
    static CritereFiltre<ConstatAudit> passage(Supplier<? extends List<ConstatAudit>> constatsCourants) {
        return CritereListe.simple(
                PASSAGE,
                "Passage",
                "Choisir un passage",
                () -> passagesPresents(constatsCourants.get()),
                CriteresAudit::numeroPassage);
    }

    /// Les passages présents, distincts et triés **numériquement**.
    ///
    /// Le tri numérique n'est pas un détail : rangés comme du texte, le passage 7 viendrait **après** le
    /// 42, ce qui rend une liste de nuits illisible. Les autres écrans trient des libellés, celui-ci
    /// range des nombres.
    ///
    /// Un constat **sans passage** (une vérification en ligne qui échoue, un fichier orphelin) n'entre
    /// pas dans la liste : il n'y aurait rien à y désigner.
    static List<String> passagesPresents(List<ConstatAudit> constats) {
        return constats.stream()
                .map(ConstatAudit::idPassage)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .toList();
    }

    private static String numeroPassage(ConstatAudit constat) {
        return constat.idPassage() == null ? null : String.valueOf(constat.idPassage());
    }

    /// **Recherche texte** de la barre : la **cible** et le **détail**, les deux colonnes en texte libre
    /// de la table. Insensible à la casse et aux accents.
    ///
    /// Les autres colonnes (gravité, catégorie, passage) ont leur puce : les inclure ici ferait répondre
    /// la recherche à « erreur » sur toutes les lignes en erreur, ce que la puce dit mieux.
    static BiPredicate<ConstatAudit, String> rechercheTexte() {
        return CriteresAudit::correspond;
    }

    private static boolean correspond(ConstatAudit constat, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(constat.cible(), aiguille) || contient(constat.detail(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }
}
