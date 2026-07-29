package fr.univ_amu.iut.commun.model;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Optional;

/// Les saisons du référentiel d'activité (#2351), telles que la ressource les nomme : `printemps`,
/// `ete`, `automne`.
///
/// ## Des fenêtres phénologiques, pas des saisons calendaires
///
/// Elles suivent le **cycle biologique** des chiroptères, et **tombent au milieu des mois** :
///
/// | Saison | Fenêtre | Ce qui s'y joue |
/// |---|---|---|
/// | Printemps | 1er avril → 15 juin | sortie d'hibernation, gestation |
/// | Été | 16 juin → 31 août | mise bas et élevage des jeunes |
/// | Automne | 1er septembre → 15 novembre | émancipation, migration, accouplements |
/// | *(hors fenêtre)* | 16 novembre → 31 mars | hibernation : pas de seuil applicable |
///
/// **Ces bornes viennent de la source, pas d'un choix du produit.** Le découpage est celui du jeu de
/// référence Vigie-Chiro, employé pour éliminer le biais saisonnier lors de la construction des seuils.
/// Il est cité tel quel par le [rapport du Groupe Mammalogique Breton (Barbosa & Dubos,
/// 2022)](https://gmb.bzh/wp-content/uploads/2022/05/BarbosaDubos_2022-ReferentielActiviteChiroBretagne.pdf) :
/// « *le cloisonnement du jeu de données de référence en trois sous-ensembles saisonniers (1er avril au
/// 15 juin : gestation / 16 juin au 31 août : mise-bas et élevage des jeunes / 1er septembre au 15
/// novembre : émancipation des jeunes, migration, accouplements) nous permet d'éliminer ce biais* », et
/// recoupé par la description du protocole [Vigie-Chiro](https://www.vigienature.fr/fr/page/participer-vigie-chiro).
///
/// Comparer une nuit à des seuils calculés sur une autre fenêtre que la sienne fausserait la classe :
/// une nuit d'août jugée à l'aune de l'automne, ou une nuit de début juin à l'aune de l'été, ne se
/// compare pas à ce qu'elle devrait.
///
/// Hors fenêtre, [#de] rend **vide** : la comparaison se fait alors « toutes saisons ». En hibernation,
/// une nuit à trois contacts n'est pas une nuit faible — c'est une nuit d'hiver, et lui appliquer un
/// seuil estival la ferait passer pour un désert.
public enum SaisonActivite {
    PRINTEMPS("printemps", "Printemps", MonthDay.of(4, 1), MonthDay.of(6, 15)),
    ETE("ete", "Été", MonthDay.of(6, 16), MonthDay.of(8, 31)),
    AUTOMNE("automne", "Automne", MonthDay.of(9, 1), MonthDay.of(11, 15));

    private final String cle;
    private final String libelle;
    private final MonthDay debut;
    private final MonthDay fin;

    SaisonActivite(String cle, String libelle, MonthDay debut, MonthDay fin) {
        this.cle = cle;
        this.libelle = libelle;
        this.debut = debut;
        this.fin = fin;
    }

    /// La clé telle qu'elle s'écrit dans la ressource (sans accent).
    public String cle() {
        return cle;
    }

    /// Le libellé affichable.
    public String libelle() {
        return libelle;
    }

    /// La saison d'une **nuit biologique**, ou vide hors fenêtre (hibernation).
    ///
    /// Les trois fenêtres ne se chevauchent pas et laissent volontairement un trou l'hiver : une nuit
    /// du 20 novembre n'appartient à aucune, et c'est le bon résultat.
    public static Optional<SaisonActivite> de(LocalDate nuit) {
        if (nuit == null) {
            return Optional.empty();
        }
        MonthDay jour = MonthDay.from(nuit);
        for (SaisonActivite saison : values()) {
            if (!jour.isBefore(saison.debut) && !jour.isAfter(saison.fin)) {
                return Optional.of(saison);
            }
        }
        return Optional.empty();
    }
}
