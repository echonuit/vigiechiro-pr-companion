package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import java.util.List;

/// Rapport d'une réactivation (#1302) : ce qui a été rebranché, ce qui a été **refusé** et pourquoi,
/// ce qui manque encore. Jamais un simple « c'est fait » : l'utilisateur voit sur quelle **preuve**
/// l'application s'est appuyée, et ce qui reste à retrouver.
///
/// @param reactivees séquences rebranchées : le fichier candidat a passé la cascade de vérification
/// @param divergentes séquences **refusées** : un fichier du même nom existait, mais ce n'était pas
///     le bon audio (empreinte, taille ou durée différentes, cris absents). Jamais rebranchées en
///     silence : rebrancher aurait produit des observations pointant sur le mauvais son
/// @param manquantes séquences sans candidat dans le dossier désigné (rien à rebrancher)
/// @param dejaPresentes séquences dont le fichier était déjà là (réactivation rejouée : sans effet)
/// @param confianceMinimale niveau de confiance le **plus faible** parmi les séquences rebranchées
///     (`null` si aucune ne l'a été) : c'est celui qu'il faut afficher, pas le meilleur
/// @param ecarts détail des divergences (nom du fichier + motif en clair), pour que l'utilisateur
///     décide en connaissance de cause
/// @param decompte disponibilité de l'audio **après** l'opération : le passage repasse en `COMPLETE`
///     ou reste en `PARTIELLE` selon le résultat
public record RapportReactivation(
        int reactivees,
        int divergentes,
        int manquantes,
        int dejaPresentes,
        NiveauConfiance confianceMinimale,
        List<EcartReactivation> ecarts,
        DecompteAudio decompte) {

    public RapportReactivation {
        ecarts = List.copyOf(ecarts);
    }

    /// Une séquence **refusée** : un fichier homonyme était là, mais la vérification l'a écarté.
    ///
    /// @param nomFichier nom de la séquence concernée
    /// @param motif ce qui a divergé, en clair (issu du [VerdictIdentite.Refusee])
    public record EcartReactivation(String nomFichier, String motif) {}

    /// L'audio du passage est-il **entièrement** revenu ?
    public boolean complete() {
        return decompte.disponibilite() == DisponibiliteAudio.COMPLETE;
    }
}
