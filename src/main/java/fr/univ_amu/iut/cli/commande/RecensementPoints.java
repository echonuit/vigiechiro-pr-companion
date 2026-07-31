package fr.univ_amu.iut.cli.commande;

import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Combien de sites portent chaque **code de point** (`Z1`, `T 1 1`…), sur un ensemble de sites lu.
///
/// ## Pourquoi ce comptage existe
///
/// Un code de point n'est unique **que dans son site** : sur le catalogue de la plateforme, `Z1` est
/// porté par des centaines de carrés. Ce fait a d'abord été établi à coups de `curl` et de script
/// jetable ; il devait devenir une capacité du produit, faute de quoi la prochaine question du même
/// genre repartirait dans un terminal Python.
///
/// ## Ce que la part signifie
///
/// La part est celle **des sites lus**, jamais de la plateforme : sur un échantillon de trois pages,
/// « 96 % » veut dire « 96 % des 300 sites que j'ai regardés ». La commande qui l'affiche doit le dire,
/// et c'est pourquoi ce calcul ne porte que des comptes bruts : il ne sait pas, lui, sur quoi il a
/// travaillé.
public final class RecensementPoints {

    /// Un code de point, et le nombre de sites qui le portent.
    public record Ligne(String code, int sites) {}

    private RecensementPoints() {}

    /// Les codes de points présents, **du plus partagé au moins partagé**, à égalité par ordre
    /// alphabétique. Un site qui porterait deux fois le même code ne le compte qu'une fois : c'est le
    /// nombre de **sites**, pas de points.
    public static List<Ligne> de(List<SiteVigieChiro> sites) {
        Map<String, Integer> parCode = new LinkedHashMap<>();
        for (SiteVigieChiro site : sites) {
            site.points().stream()
                    .map(point -> point.code())
                    .filter(code -> code != null && !code.isBlank())
                    .distinct()
                    .forEach(code -> parCode.merge(code, 1, Integer::sum));
        }
        List<Ligne> lignes = new ArrayList<>();
        parCode.forEach((code, nombre) -> lignes.add(new Ligne(code, nombre)));
        lignes.sort(Comparator.comparingInt(Ligne::sites).reversed().thenComparing(Ligne::code));
        return List.copyOf(lignes);
    }
}
