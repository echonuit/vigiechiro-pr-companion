package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// Restreindre des observations par **seuil de probabilité Tadarida**, en ligne de commande (#2971).
///
/// Pendant CLI de la puce « Proba » de l'écran, dont il reprend la règle **exactement**, y compris sur
/// le cas qui surprend.
///
/// ## Les détections sans probabilité sont conservées
///
/// C'est la règle de la puce, et elle mérite d'être dite : une détection sans probabilité n'a pas de
/// confiance **comparable** au seuil. L'écarter reviendrait à décider qu'elle est mauvaise alors qu'on
/// n'en sait rien, et à perdre une ligne qu'il faut justement aller revoir. Toute autre règle ferait
/// diverger les deux surfaces sur le même mot.
///
/// ## L'échelle est 0..1, pas le pourcentage de l'écran
///
/// L'IHM affiche « 74 % » mais son curseur est un `Slider(0, 1, 0.5)` : l'échelle interne est déjà
/// celle-ci. Surtout, `lister-observations` **imprime déjà** `probTadarida` brut (`0.74`). Entrée et
/// sortie d'un même appel parlent ainsi la même langue, là où accepter `90` obligerait à convertir
/// mentalement entre ce qu'on demande et ce qu'on lit.
///
/// ## Hors bornes, c'est un refus
///
/// `--proba-min 90` est le réflexe du pourcentage, et borner en silence rendrait zéro ligne sans dire
/// pourquoi. Le refus nomme la plage **et** la raison, parce que l'erreur vient d'une confusion d'unité
/// et non d'une faute de frappe.
///
/// ## Ce qu'il ne fait pas, contrairement à [FiltresLieu]
///
/// Un résultat **vide** n'est pas un refus ici. Un seuil est un nombre : il ne peut pas désigner
/// quelque chose qui n'existe pas, et « aucune détection au-dessus de 0,99 » est une réponse, pas une
/// faute. Un nom de lieu, lui, se tape de travers, et c'est ce qui justifiait le refus là-bas.
public final class FiltresProbabilite {

    private FiltresProbabilite() {}

    /// Les lignes dont la probabilité Tadarida atteint `seuil`, **plus celles qui n'en ont pas**.
    /// `seuil` nul n'écarte rien.
    ///
    /// @throws RegleMetierException si le seuil sort de l'intervalle `[0, 1]`
    public static List<LigneObservationAudio> parSeuilMinimal(List<LigneObservationAudio> lignes, Double seuil) {
        if (seuil == null) {
            return lignes;
        }
        if (seuil < 0 || seuil > 1) {
            throw new RegleMetierException(String.format(
                    Locale.FRENCH,
                    "Seuil hors bornes : --proba-min %s. La plage est 0 à 1 (l'écran affiche des "
                            + "pourcentages, la ligne de commande compte en 0..1) : 90 %% s'écrit 0.9.",
                    seuil));
        }
        return lignes.stream()
                .filter(ligne -> ligne.probTadarida() == null || ligne.probTadarida() >= seuil)
                .toList();
    }

    /// L'avertissement à afficher quand le seuil **a tout écarté** : la meilleure probabilité du lot,
    /// pour que l'utilisateur sache de combien il s'est trompé.
    ///
    /// Un ensemble vide n'est pas une erreur ici (cf. l'en-tête), mais c'est le seul filtre où l'on peut
    /// légitimement tout écarter **sans que rien ne dise ce qu'on a raté**. Un lieu inexistant se refuse
    /// et se corrige ; un seuil trop haut rend une archive vide, valide et muette. Nommer la meilleure
    /// probabilité présente transforme ce silence en information actionnable : « 0,74 » dit à la fois
    /// que le lot n'était pas vide et de combien abaisser le seuil.
    ///
    /// `avantSeuil` est le lot **tel qu'il était avant** ce filtre : c'est lui qui porte l'information.
    /// Rien n'est dit si ce lot était déjà vide, le seuil n'y étant alors pour rien.
    ///
    /// Le maximum est toujours défini quand le résultat est vide : une ligne sans probabilité étant
    /// **toujours** conservée, un résultat vide implique que toutes les lignes en portaient une.
    public static Optional<String> avertissementSeuilTropHaut(List<LigneObservationAudio> avantSeuil, Double seuil) {
        if (seuil == null
                || avantSeuil.isEmpty()
                || !parSeuilMinimal(avantSeuil, seuil).isEmpty()) {
            return Optional.empty();
        }
        return avantSeuil.stream()
                .map(LigneObservationAudio::probTadarida)
                .filter(Objects::nonNull)
                .max(Double::compare)
                .map(meilleure -> String.format(
                        Locale.FRENCH,
                        "Aucune détection n'atteint %.2f. La plus sûre du lot est à %.2f : "
                                + "abaissez le seuil pour l'atteindre.",
                        seuil,
                        meilleure));
    }
}
