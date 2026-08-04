package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/// Restreindre des [ContactHoraire] **en ligne de commande** (#3059), aux mêmes conditions que la barre
/// de filtres de l'écran Activité.
///
/// ## Parité stricte, et pourquoi
///
/// L'écran offre cinq critères ; `exporter-activite` n'en offrait **aucun**, sachant exporter une nuit ou
/// toutes et rien entre les deux. N'en livrer qu'un ou deux aurait reproduit la **parité de façade** que
/// #2971 a déjà coûtée : vraie sur le cas qu'on regarde, fausse dès qu'on resserre comme on le fait en
/// vrai. Les cinq sont donc offerts, le **lieu** par [fr.univ_amu.iut.validation.model.FiltresLieu]
/// généralisé, les quatre autres ici.
///
/// ## Un écart assumé, le même qu'en revue audio
///
/// Le **point** n'est pas une dimension de lieu en ligne de commande. Le schéma pose
/// `UNIQUE(site_id, code)` : un code seul désigne autant de lieux qu'il y a de carrés. L'écran s'en tire
/// en l'affichant **qualifié** (« 640380 · A1 », #2992), ce qui suppose une liste sous les yeux ;
/// reproduire cette forme imposerait un point médian dans une valeur d'option, à échapper dans chaque
/// script. C'est mot pour mot l'arbitrage de `exporter-sons`, et deux commandes qui traitent le lieu
/// différemment seraient pires que la limite elle-même.
///
/// ## Ce qui refuse, et ce qui rend vide
///
/// Un critère qui **désigne** quelque chose refuse quand ce quelque chose n'existe pas, en nommant ce qui
/// est présent : une archive vide en code 0 est un succès qui ne contient rien, et le script enchaîne.
/// Un critère qui **qualifie** (la nature d'une nuit, l'enjeu d'une espèce) rend légitimement un ensemble
/// vide : « aucune nuit opportuniste » est une réponse, pas une faute de frappe.
public final class FiltresActivite {

    private FiltresActivite() {}

    /// Les dimensions de lieu **comparables en ligne de commande** : commune et carré, sans le point.
    ///
    /// Le carré est écrit comme l'écran l'affiche, « 640380 · Vallon » (#3157), pour que le refus de
    /// `--lieu` nomme les lieux tels qu'on les y voit. La correspondance restant partielle,
    /// `--lieu 640380` et `--lieu vallon` retiennent l'un et l'autre ce carré.
    public static List<String> dimensionsLieu(ContactHoraire contact) {
        return java.util.stream.Stream.of(
                        contact.commune(), LieuQualifie.qualifier(contact.numeroCarre(), contact.nomSite()))
                .filter(valeur -> valeur != null && !valeur.isBlank())
                .toList();
    }

    /// Les contacts de la **nuit** demandée (date du soir, `AAAA-MM-JJ`). `nuit` nul n'écarte rien.
    ///
    /// @throws RegleMetierException si la date est illisible, ou si aucun contact n'appartient à cette nuit
    public static List<ContactHoraire> parNuit(List<ContactHoraire> contacts, String nuit) {
        if (nuit == null || nuit.isBlank()) {
            return contacts;
        }
        LocalDate demandee = lire(nuit);
        List<ContactHoraire> retenus =
                contacts.stream().filter(c -> demandee.equals(c.nuit())).toList();
        if (retenus.isEmpty()) {
            throw new RegleMetierException("Aucun contact la nuit du " + demandee + ". Nuits présentes : "
                    + resumer(contacts.stream().map(ContactHoraire::nuit)) + ".");
        }
        return retenus;
    }

    /// Les contacts du **taxon parent** demandé (correspondance partielle, insensible casse/accents, comme
    /// `--lieu`). `groupe` nul n'écarte rien.
    ///
    /// @throws RegleMetierException si aucun contact ne relève de ce taxon parent
    public static List<ContactHoraire> parTaxonParent(List<ContactHoraire> contacts, String groupe) {
        if (groupe == null || groupe.isBlank()) {
            return contacts;
        }
        String demande = NormalisationTexte.normaliser(groupe);
        List<ContactHoraire> retenus = contacts.stream()
                .filter(c -> c.groupe() != null
                        && NormalisationTexte.normaliser(c.groupe()).contains(demande))
                .toList();
        if (retenus.isEmpty()) {
            throw new RegleMetierException("Aucun contact pour le taxon parent « " + groupe
                    + " ». Taxons parents présents : "
                    + resumer(contacts.stream().map(ContactHoraire::groupe)) + ".");
        }
        return retenus;
    }

    /// Les contacts des nuits de la **nature** demandée. `nature` nul n'écarte rien.
    ///
    /// Ne refuse pas sur un résultat vide : « aucune nuit opportuniste » est une réponse. Une nuit sans
    /// marquage relève du protocole, qui est le cas courant.
    ///
    /// @throws RegleMetierException si la nature n'est ni `protocole` ni `opportuniste`
    public static List<ContactHoraire> parNature(
            List<ContactHoraire> contacts, String nature, Set<Long> nuitsOpportunistes) {
        if (nature == null || nature.isBlank()) {
            return contacts;
        }
        boolean opportuniste =
                switch (NormalisationTexte.normaliser(nature)) {
                    case "opportuniste" -> true;
                    case "protocole" -> false;
                    default ->
                        throw new RegleMetierException("Nature inconnue : --nature " + nature
                                + ". Valeurs acceptées : protocole, opportuniste.");
                };
        return contacts.stream()
                .filter(c -> opportuniste == estOpportuniste(c, nuitsOpportunistes))
                .toList();
    }

    /// Les contacts d'espèces **prioritaires** au sens du Plan National d'Actions Chiroptères.
    ///
    /// Ne refuse pas sur un résultat vide, pour la même raison que [#parNature] : c'est une qualification,
    /// et « aucune espèce à enjeu cette nuit-là » est une information, souvent celle qu'on cherchait.
    public static List<ContactHoraire> aEnjeu(List<ContactHoraire> contacts, Predicate<String> estPrioritaire) {
        return contacts.stream()
                .filter(c -> c.taxon() != null && estPrioritaire.test(c.taxon()))
                .toList();
    }

    /// L'avertissement à dire quand `--a-enjeu` ne peut **rien** retenir faute de référentiel, ou vide.
    ///
    /// C'est le seul filtre de la commande dont un résultat vide a **deux causes opposées** : aucune
    /// espèce prioritaire dans ces nuits, ou aucun référentiel du tout. Les deux rendaient le même fichier
    /// vide en code 0, et elles appellent des conduites contraires - lire le résultat, ou réparer une
    /// installation (ADR 3048 : une sortie machine ne retire pas, elle **dit**).
    ///
    /// Rien n'est retiré : le CSV garde ses colonnes et son code de sortie. Même patron que
    /// [fr.univ_amu.iut.validation.model.FiltresProbabilite#avertissementSeuilTropHaut] - la décision est
    /// une fonction pure, la surface se contente de l'imprimer.
    public static Optional<String> avertissementReferentielVide(Set<String> prioritaires) {
        if (!prioritaires.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Référentiel des espèces à enjeu vide : --a-enjeu ne peut rien retenir. Le "
                + "fichier sera vide pour cette raison, et non parce qu'aucune espèce prioritaire n'a été "
                + "détectée.");
    }

    private static boolean estOpportuniste(ContactHoraire contact, Set<Long> nuitsOpportunistes) {
        return contact.idPassage() != null && nuitsOpportunistes.contains(contact.idPassage());
    }

    private static LocalDate lire(String nuit) {
        try {
            return LocalDate.parse(nuit.trim());
        } catch (DateTimeParseException illisible) {
            throw new RegleMetierException("Nuit illisible : --nuit " + nuit + ". Format attendu : AAAA-MM-JJ.");
        }
    }

    /// Les valeurs présentes, sans doublon ni nul, triées : ce qu'un refus doit nommer pour être corrigeable.
    private static String resumer(java.util.stream.Stream<?> valeurs) {
        List<String> presentes = valeurs.filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .sorted()
                .toList();
        return presentes.isEmpty() ? "aucune" : String.join(", ", presentes);
    }
}
