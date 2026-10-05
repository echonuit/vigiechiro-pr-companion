"""Ce qu un apercu borne montre, et les deux gestes qui l ouvrent (#5829)."""

from __future__ import annotations

import sys

# Les deux gestes qui ouvrent un apercu borne. Ils sont lus par `_a_montrer`, donc les treize gardes
# qui bornent les recoivent sans qu aucun ne change.
TOUT_MONTRER = "--tous-les-suspects"
FILTRE = "--suspect-contenant"


def _a_montrer(
    suspects: list[str], apercu: int | None, arguments: list[str] | None = None
) -> tuple[list[str], str]:
    """Les suspects à imprimer et la ligne qui dit ce qui est tu. Fonction PURE, pour le témoin.

    ## Pourquoi un apercu borne avait besoin de ces deux gestes

    Un apercu montre les N **premiers** dans l ordre que le garde a choisi, et aucun garde ne choisit
    « le plus recemment ajoute » : `releve-les-harnais-muets.py` trie par nombre de sites decroissant,
    d autres par chemin. Sur un cliquet, le suspect neuf est le (N+1)e par construction, donc aucune
    valeur de borne inferieure au cliquet ne le fait paraitre. Trois sessions l ont vecu en vingt-quatre
    heures, et les trois ont du importer une fonction du garde depuis un interpreteur pour trouver leur
    propre fichier (#5829).

    **Le verdict ne depend PAS de ce que cette fonction rend.** `rapporte` le calcule sur
    `len(suspects)`, la liste entiere. Un filtre ne peut donc pas faire passer un garde, et c est ce
    qui rend ce geste sans danger.

    ## Pourquoi un filtre plutot qu un tri

    Le geste que les trois sessions ont reellement fait etait de **filtrer** : « que dit ce garde de CE
    fichier ? ». Elles connaissaient le fichier, puisqu elles venaient de l ecrire. Trier par recence
    aurait demande a ce module de connaitre `git`, et laisser la porte passer son diff par
    l environnement aurait fait dependre la sortie d un garde de qui l a lance - ce que le depot refuse
    en propres termes, « un garde qui ment selon la machine ne vaut rien ».

    ## Le vide du filtre dit son denominateur

    « Aucun suspect ne contient X, sur 82 » et « aucun suspect » ne sont pas la meme reponse. Un filtre
    qui tairait son denominateur fabriquerait un faux soulagement, ce qui serait pire que la borne qu il
    remplace.
    """
    args = sys.argv[1:] if arguments is None else arguments
    if FILTRE in args:
        indice = args.index(FILTRE)
        motif = args[indice + 1] if indice + 1 < len(args) else ""
        retenus = [s for s in suspects if motif in s]
        quoi = f"« {motif} »" if motif else "un motif vide"
        return retenus, (
            f"  {len(retenus)} suspect(s) sur {len(suspects)} contiennent {quoi}."
            if retenus
            else f"  AUCUN des {len(suspects)} suspects ne contient {quoi}."
        )
    if TOUT_MONTRER in args or apercu is None or len(suspects) <= apercu:
        return suspects, ""
    return suspects[:apercu], (
        f"  … et {len(suspects) - apercu} autres, non montrés (aperçu borné à {apercu}).\n"
        f"  Pour les voir : {TOUT_MONTRER}. Pour n en lire qu un : {FILTRE} <texte>."
    )


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de `_a_montrer`, joues par l auto-test de `releve-les-harnais-muets.py`.

    Un gage qu aucun harnais n appelle est inerte, et `verifie_gages_joues.py` le refuse (ADR 5483).
    Ce garde-la est choisi parce que c est le sien qui a fait decouvrir le defaut : il borne son
    apercu a douze et rend quatre-vingt-deux suspects.
    """
    cent = [f"fichier{n:03d}.py" for n in range(1, 83)]
    bornes, aveu_borne = _a_montrer(cent, 12, [])
    tous, aveu_tous = _a_montrer(cent, 12, [TOUT_MONTRER])
    trouve, aveu_trouve = _a_montrer(cent, 12, [FILTRE, "fichier042"])
    vide, aveu_vide = _a_montrer(cent, 12, [FILTRE, "absent"])
    sans_borne, aveu_sans = _a_montrer(cent, None, [])
    court, aveu_court = _a_montrer(cent[:5], 12, [])
    nu, aveu_nu = _a_montrer(cent, 12, [FILTRE])
    return [
        ("un apercu borne montre sa borne", len(bornes) == 12),
        ("son aveu nomme le geste qui tout montre", TOUT_MONTRER in aveu_borne),
        ("son aveu nomme aussi le filtre", FILTRE in aveu_borne),
        (f"{TOUT_MONTRER} leve la borne", len(tous) == 82),
        (f"{TOUT_MONTRER} n a plus rien a avouer", aveu_tous == ""),
        ("le filtre ne rend que ce qui correspond", trouve == ["fichier042.py"]),
        ("le filtre qui trouve dit son denominateur", "sur 82" in aveu_trouve),
        ("le filtre qui ne trouve rien ne rend rien", vide == []),
        # Le CONTRASTE qui compte : « aucun suspect » et « aucun suspect ne contient X, sur 82 » ne
        # sont pas la meme reponse. Sans ce cas, un filtre muet fabriquerait un faux soulagement.
        (
            "le vide du filtre dit AUCUN et son denominateur",
            "AUCUN" in aveu_vide and "82" in aveu_vide,
        ),
        (
            "sans borne, tout est montre et rien n est avoue",
            len(sans_borne) == 82 and aveu_sans == "",
        ),
        ("une liste plus courte que la borne n avoue rien", len(court) == 5 and aveu_court == ""),
        ("le filtre sans son argument ne leve pas", len(nu) == 82),
        ("et il dit que son motif etait vide", "motif vide" in aveu_nu),
    ]
