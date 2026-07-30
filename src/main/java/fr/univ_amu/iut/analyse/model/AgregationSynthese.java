package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Construit la **synthèse d'une nuit** (#2351) : une ligne par espèce, avec ce qu'on en a détecté et
/// ce que ce nombre vaut au regard du référentiel d'activité.
///
/// Pur calcul, sans base ni IHM, à côté de [AgregationAnalyse] : la CLI et l'écran l'appellent tous
/// deux, et le tableau ne peut pas différer de l'un à l'autre.
///
/// ## La bascule « identifications validées seulement »
///
/// Elle ne masque pas des lignes : elle **recalcule tout**. Une espèce dont aucune observation n'est
/// validée disparaît alors du tableau, et les contacts des autres baissent : ce qui change leur classe
/// d'activité. C'est le but : on veut pouvoir lire la nuit telle que Tadarida la propose, puis telle
/// qu'on l'a soi-même validée, et voir l'écart.
public final class AgregationSynthese {

    private AgregationSynthese() {}

    /// La synthèse des lignes fournies, triée par contacts décroissants puis par nom : ce qu'on a le
    /// plus entendu se lit en premier.
    ///
    /// @param lignes les observations de la nuit, déjà filtrées si besoin
    /// @param validesSeulement ne retenir que les observations validées ou corrigées
    /// @param referentiel le référentiel d'activité
    /// @param contexte à quoi comparer (saison, région, milieu)
    public static List<LigneSynthese> de(
            List<LigneObservationAudio> lignes,
            boolean validesSeulement,
            ReferentielActivite referentiel,
            ContexteActivite contexte) {
        Map<String, Cumul> parTaxon = new LinkedHashMap<>();
        for (LigneObservationAudio ligne : lignes) {
            if (validesSeulement && !estRevue(ligne)) {
                continue;
            }
            String taxon = ligne.taxonRetenu();
            if (taxon == null) {
                // Une séquence non identifiée n'est pas une espèce : elle n'a pas de ligne de synthèse.
                continue;
            }
            parTaxon.computeIfAbsent(taxon, code -> new Cumul(code, ligne.nomEspece(), ligne.groupe()))
                    .ajouter(ligne);
        }
        List<LigneSynthese> synthese = new ArrayList<>();
        for (Cumul cumul : parTaxon.values()) {
            synthese.add(cumul.versLigne(referentiel, contexte));
        }
        synthese.sort(Comparator.comparingInt(LigneSynthese::contacts)
                .reversed()
                .thenComparing(LigneSynthese::nomEspece, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(synthese);
    }

    /// Une observation **revue** : validée telle quelle, ou corrigée vers un autre taxon. Les deux sont
    /// des jugements de l'observateur, par opposition à une proposition de Tadarida qu'on n'a pas encore
    /// regardée.
    private static boolean estRevue(LigneObservationAudio ligne) {
        return ligne.statut() == StatutObservation.VALIDEE || ligne.statut() == StatutObservation.CORRIGEE;
    }

    /// Cumul en cours de construction pour un taxon.
    private static final class Cumul {

        private final String codeTaxon;
        private final String nomEspece;
        private final String groupe;
        private int contacts;

        /// Les fichiers **distincts** : un même fichier porte souvent plusieurs contacts, et les compter
        /// une fois chacun est précisément ce qui distingue une activité concentrée d'une activité
        /// diffuse.
        private final Set<String> fichiers = new LinkedHashSet<>();

        private Cumul(String codeTaxon, String nomEspece, String groupe) {
            this.codeTaxon = codeTaxon;
            this.nomEspece = nomEspece;
            this.groupe = groupe;
        }

        private void ajouter(LigneObservationAudio ligne) {
            contacts++;
            if (ligne.nomFichier() != null) {
                fichiers.add(ligne.nomFichier());
            }
        }

        private LigneSynthese versLigne(ReferentielActivite referentiel, ContexteActivite contexte) {
            Optional<SeuilsActivite> seuils = referentiel.pour(codeTaxon, contexte);
            return new LigneSynthese(
                    codeTaxon,
                    nomEspece != null ? nomEspece : codeTaxon,
                    groupe,
                    contacts,
                    fichiers.size(),
                    seuils.map(s -> ClasseActivite.de(contacts, s)),
                    seuils,
                    referentiel.couvre(codeTaxon));
        }
    }
}
