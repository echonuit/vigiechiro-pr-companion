package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.SeuilsActivite;
import java.util.Optional;

/// Une ligne de la **synthèse d'une nuit** (#2351) : une espèce, ce qu'on en a détecté, et ce que ce
/// nombre vaut.
///
/// ## Pourquoi les fichiers distincts à côté des contacts
///
/// Un contact est un cri ; un fichier est une séquence d'enregistrement. Deux cents contacts répartis
/// sur deux fichiers ne racontent pas la même nuit que deux cents contacts sur cent-cinquante
/// fichiers : le premier cas peut être un individu qui chasse devant le micro, le second une activité
/// diffuse toute la nuit. Le référentiel compte en **contacts** ; les fichiers disent si ces contacts
/// sont concentrés.
///
/// ## La classe, quand elle existe
///
/// [#classe] est **vide** dans deux cas qu'il ne faut pas confondre à l'écran :
///
/// - le taxon n'est **pas couvert** par le référentiel (orthoptère, oiseau, bruit) — à écrire
///   « non couvert par le référentiel » ;
/// - le référentiel le couvre mais n'a **aucun seuil** pour ce contexte.
///
/// Dans les deux cas, une cellule vide se lirait comme une donnée manquante. [#couvertParLeReferentiel]
/// permet de les distinguer.
///
/// @param codeTaxon code Tadarida retenu
/// @param nomEspece nom vernaculaire, ou le code faute de mieux
/// @param groupe catégorie du référentiel taxonomique (« Chiroptères », « Oiseaux »…)
/// @param contacts nombre de contacts retenus
/// @param fichiers nombre de fichiers distincts d'où ils viennent
/// @param classe où se situe ce nombre, ou vide
/// @param seuils les quantiles retenus, à afficher **à côté** de la classe
/// @param couvertParLeReferentiel le taxon figure-t-il au référentiel, indépendamment du contexte
public record LigneSynthese(
        String codeTaxon,
        String nomEspece,
        String groupe,
        int contacts,
        int fichiers,
        Optional<ClasseActivite> classe,
        Optional<SeuilsActivite> seuils,
        boolean couvertParLeReferentiel) {

    public LigneSynthese {
        classe = classe == null ? Optional.empty() : classe;
        seuils = seuils == null ? Optional.empty() : seuils;
    }

    /// La classe est-elle donnée à titre **indicatif** ? Vrai quand les seuils retenus viennent d'une
    /// déclinaison peu fiable, faute de mieux : l'écran écrit alors « Moyenne (indicatif) ».
    public boolean indicatif() {
        return seuils.map(SeuilsActivite::indicatif).orElse(false);
    }

    /// Ce qu'il faut écrire dans la colonne de classe, y compris quand il n'y a pas de classe. Jamais
    /// vide : une cellule blanche se lit comme une donnée manquante, alors qu'ici l'absence a un sens
    /// et il est différent selon le cas.
    public String libelleClasse() {
        if (classe.isEmpty()) {
            return couvertParLeReferentiel ? "Pas de seuil pour ce contexte" : "Non couvert par le référentiel";
        }
        return indicatif()
                ? classe.get().libelle() + " (indicatif)"
                : classe.get().libelle();
    }
}
