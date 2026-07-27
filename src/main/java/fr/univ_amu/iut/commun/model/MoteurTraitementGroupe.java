package fr.univ_amu.iut.commun.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Applique une [ActionGroupee] à une suite de passages (#2357), **un à la fois**, et rend compte de
/// chacun.
///
/// ## Séquentiel, délibérément
///
/// Le moteur ne parallélise **rien**. Les gestes qu'il enchaîne parallélisent déjà en interne quand
/// c'est utile — le dépôt téléverse cinq unités de front, plafond calqué sur le front web. Lancer N
/// passages simultanément multiplierait ce plafond par N et transformerait un confort en rafale que
/// le serveur rejette. Le parallélisme reste donc **à l'intérieur** d'un passage, jamais entre eux.
///
/// ## L'annulation s'arrête *entre* deux passages
///
/// Le jeton est consulté **avant** chaque passage, jamais pendant. Un passage commencé va donc au
/// bout, et tout passage non commencé est rendu [IssueTraitement.Statut#NON_TRAITE] : chacun est soit
/// dans son état d'avant, soit dans son état d'après, **jamais entre les deux**. C'est le contrat qui
/// rend un lot interrompu reprenable — et il est tenu par construction, pas par vigilance.
///
/// ## Un échec n'arrête pas le lot
///
/// Une action qui lève est enregistrée en échec **avec son motif**, et le lot continue : rentrer de
/// terrain avec six cartes et voir cinq passages abandonnés parce que le premier a échoué serait le
/// contraire du service rendu.
public final class MoteurTraitementGroupe {

    /// Journal muet, pour les appelants qui n'en veulent pas (tests, CLI silencieux).
    private static final Consumer<String> SANS_JOURNAL = ligne -> {};

    /// Exécute `action` sur chaque cible, sans journal.
    public ResultatTraitementGroupe executer(ActionGroupee action, List<CiblePassage> cibles, JetonAnnulation jeton) {
        return executer(action, cibles, jeton, SANS_JOURNAL);
    }

    /// Exécute `action` sur chaque cible et rend le sort de chacune.
    ///
    /// Chaque ligne remise à `journal` est **préfixée du passage concerné** : sans cela, un lot
    /// interrompu est indiagnostiquable — on sait qu'il a échoué, pas où.
    ///
    /// @param jeton consulté avant chaque passage ; une fois annulé, les passages restants sont rendus
    ///     `NON_TRAITE` sans être touchés
    /// @return une issue par cible, dans l'ordre de soumission
    public ResultatTraitementGroupe executer(
            ActionGroupee action, List<CiblePassage> cibles, JetonAnnulation jeton, Consumer<String> journal) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(cibles, "cibles");
        Objects.requireNonNull(jeton, "jeton");
        Objects.requireNonNull(journal, "journal");

        List<IssueTraitement> issues = new ArrayList<>();
        boolean interrompu = false;
        for (CiblePassage cible : cibles) {
            if (jeton.estAnnule()) {
                interrompu = true;
                issues.add(IssueTraitement.nonTraite(cible));
                continue;
            }
            issues.add(traiter(action, cible, journal));
        }
        return new ResultatTraitementGroupe(action.libelle(), issues, interrompu);
    }

    private IssueTraitement traiter(ActionGroupee action, CiblePassage cible, Consumer<String> journal) {
        var motifNonEligible = action.motifNonEligible(cible);
        if (motifNonEligible.isPresent()) {
            journal.accept(prefixe(cible) + "écarté : " + motifNonEligible.get());
            return IssueTraitement.ecarte(cible, motifNonEligible.get());
        }
        journal.accept(prefixe(cible) + action.libelle() + "…");
        try {
            action.executer(cible);
            journal.accept(prefixe(cible) + "terminé");
            return IssueTraitement.reussi(cible);
        } catch (RuntimeException echec) {
            String motif = echec.getMessage() == null ? echec.getClass().getSimpleName() : echec.getMessage();
            journal.accept(prefixe(cible) + "ÉCHEC : " + motif);
            return IssueTraitement.echec(cible, motif);
        }
    }

    /// Préfixe de journal identifiant le passage (critère d'acceptation de #2357).
    private static String prefixe(CiblePassage cible) {
        return "[" + cible.designation() + "] ";
    }
}
