#!/usr/bin/env python3
"""Rapport hebdomadaire de conformité aux ADR.

Il fait tourner tous les scripts de vérification et agrège leur sortie normalisée en un tableau
Markdown, pour mesurer l'écart et la dette d'une semaine sur l'autre. Deux sections :

- **Cliquets** (`probable`) : chaque script rend `suspects=N | cliquet=M | verdict=…`. Le rapport
  rappelle la marge, et surtout signale les cliquets À RESSERRER - ceux dont la réalité (`suspects`)
  est passée sous la marge. C'est le carburant de la calibration : un cliquet qui ne descend pas quand
  le dépôt s'améliore laisse une marge morte où une régression pourrait se glisser sans rougir.
- **Loupes** (`humaine`) : indicatif seul. On compte les candidats à revoir, sans verdict.

Le rapport N'ÉCHOUE PAS sur une régression de cliquet : ce n'est pas son rôle (c'est celui du script,
en CI, sur la PR fautive). Son rôle est de donner l'image d'ensemble et de proposer les resserrements.

Usage : python3 scripts/adr/rapport.py [--markdown]
Sans --markdown, sortie texte lisible en console.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import (
    RACINE_DEPOT,
    cas_d_auto_test,
    champs_du_verdict,
    sort_si_contrat_demande,
)

ICI = pathlib.Path(__file__).parent
# Le champ `lus` (issue #5007) se lit en groupe NON capturant, et ce n'est pas un detail : les
# indices de groupe sont consommes en clair plus bas, et les tuples portent CINQ elements depuis
# que `lus` est capture (#5053). Le decalage que redoutait la note d'origine est tenu par un cas :
# `test_rapport_et_resserrement` passe des tuples ecrits en dur a `resserrements()`, si bien qu'une
# arite changee sans lui leve une erreur de deballage. Constate rouge avant d'ecrire ce changement.
#
# `?` est accepte parce qu'un garde qui ne declare pas son compte n'est pas toujours une panne : les
# quatre muets qui restent sont des exclusions ECRITES, non des retardataires (#5015).
# ⟨les lignes de cliquet et de plancher se lisent par le motif PARTAGE⟩ Ce rapport en portait deux
# versions strictes, qui enumeraient les champs dans l ordre. Elles refusaient une ligne qui en gagne
# un, et un `finditer` qui ne trouve rien ne leve pas : la ligne aurait cesse d etre comptee sans que
# rien ne rougisse, et ce rapport AGREGE, donc une ligne ratee est un chiffre manquant et non une
# erreur visible. Le motif vit desormais dans `_commun`, borne de la meme facon pour ses trois
# lecteurs, et les champs se prennent en seconde passe (#5830).
LIGNE_LOUPE = re.compile(r"^LOUPE (\d+) \| lus=(\?|\d+) \| candidats=(\d+)$", re.M)
# Le PLANCHER est la polarite inverse du cliquet, et il a sa propre ligne. Ce rapport ne la lisait
# pas : le garde des renvois annoncait « a-relever » a chaque passage, sans que rien ne le montre.
# Les deux familles passent par le meme motif partage depuis #5830, et `dispositif` les separe.


def executer(script: pathlib.Path) -> str:
    """Lance un script de vérification et rend sa sortie. Le code de sortie est ignoré : le rapport
    observe, il ne juge pas - un cliquet en régression fait déjà rougir la CI ailleurs.

    Le script tourne DANS le dépôt, pas dans le répertoire de l'appelant (issue #4781). Sans `cwd`,
    chaque garde héritait du répertoire courant : lancé d'ailleurs, cinq cliquets sur dix-huit
    rendaient une autre mesure, dont quatre tombaient à zéro faute de trouver quoi que ce soit.
    `resserre_cliquets.py` agit sur ces verdicts et ÉCRIT : il ramenait alors quatre `ratchet:` à
    zéro dans les vraies ADR, en annonçant un succès. Une ligne ici couvre les cinq, et le garde
    qu'on écrira demain."""
    fini = subprocess.run(
        [sys.executable, str(script)],
        capture_output=True,
        text=True,
        check=False,
        cwd=RACINE_DEPOT,
    )
    return fini.stdout + fini.stderr


def collecter(executeur=None, scripts=None):
    """Les verdicts, et la liste de ceux que ce rapport n a PAS su lire (article A3).

    ## La couture, et ce que son absence a coute

    `executeur` et `scripts` sont INJECTABLES (ADR 3624). Sans eux, le seul moyen d eprouver cette
    fonction etait de la laisser lancer les trente-quatre gardes du dossier : le cas
    `test_resserre_cliquets_appelle_le_rapport` de `verifie_scripts.py` mettait **134 s** pour
    apprendre que `collecter()` rend quatre listes. C etait 97 % du cout de ce garde, et 84 % de ce
    qui restait de la batterie locale apres #5377. Le travail etait fait DEUX fois, puisque la porte
    lance deja ces memes gardes un par un (#5389).

    Un script lance dont aucune ligne ne correspond rendait un rapport silencieux : il manquait dans
    le tableau, et rien ne disait qu il manquait. Mesure du 2026-08-28 : sur vingt scripts, TROIS
    etaient dans ce cas, dont un plancher qui annoncait `verdict=a-relever` depuis on ne sait quand.
    Un dispositif dit ce qu il couvre, et ce qu il n a pas pu lire.
    """
    cliquets, planchers, loupes, muets = [], [], [], []
    lance = executeur or executer
    for script in sorted(ICI.glob("[0-9]*.py")) if scripts is None else scripts:
        sortie = lance(script)
        # `verdicts` compte les LIGNES lues dans cette sortie, et non les unites qu'un garde a lues.
        # Les deux s'appelaient `lus`, a un caractere pres du champ : deux sens sous un nom.
        verdicts = 0
        for ligne in sortie.splitlines():
            champs = champs_du_verdict(ligne)
            if champs is None:
                continue
            # ⟨`lus` peut MANQUER, et c est declare⟩ La docstring de `rapporte_plancher` donne en
            # exemple une ligne sans ce champ. Un motif strict la refusait ; ici elle est comptee, et
            # son `lus` se lit « ? » comme celui d une population qui ne se compte pas.
            lus = champs.get("lus", "?")
            if champs["dispositif"] == "ADR":
                cliquets.append(
                    (
                        champs["numero"],
                        lus,
                        int(champs["suspects"]),
                        int(champs["cliquet"]),
                        champs["verdict"],
                    )
                )
            else:
                planchers.append(
                    (
                        champs["numero"],
                        lus,
                        int(champs["mesure"]),
                        int(champs["plancher"]),
                        champs["verdict"],
                    )
                )
            verdicts += 1
        if not verdicts:
            muets.append((script.name, premiere_ligne_de_verdict(sortie)))
    # ⟨la seconde famille passe par la MEME couture⟩ Elle appelait `executer` en dur : un executeur
    # injecte ne couvrait donc que la moitie du parcours, et un cas qui croyait ne rien lancer
    # lancait encore les loupes, dont deux interrogent la forge.
    for script in sorted(ICI.glob("loupe-*.py")) if scripts is None else []:
        sortie = lance(script)
        verdicts = 0
        for m in LIGNE_LOUPE.finditer(sortie):
            loupes.append((m.group(1), m.group(2), int(m.group(3))))
            verdicts += 1
        if not verdicts:
            muets.append((script.name, premiere_ligne_de_verdict(sortie)))
    return cliquets, planchers, loupes, muets


def premiere_ligne_de_verdict(sortie: str) -> str:
    """Ce que le script a rendu qui RESSEMBLE a un verdict, pour que le rapport montre l ecart.

    Sans cet extrait, le lecteur sait qu un script est muet et doit le relancer a la main pour savoir
    pourquoi. Avec lui, l ecart entre ce qui est rendu et ce qui est attendu se lit sur place.
    """
    for ligne in sortie.split("\n"):
        if ligne.startswith(("ADR ", "LOUPE ", "PLANCHER ")) and "|" in ligne:
            return ligne.strip()
    return "aucune ligne de verdict"


def rendre(cliquets, planchers, loupes, muets, markdown: bool) -> str:
    h1, h2, li = ("## ", "### ", "- ") if markdown else ("== ", "-- ", "  ")
    out = [f"{h1}Rapport de conformité aux ADR", ""]

    out.append(f"{h2}Cliquets (vérifications « probable »)")
    if markdown:
        out += ["", "| ADR | lus | suspects | cliquet | verdict |", "|---|---|---|---|---|"]
        for num, lus, s, c, v in cliquets:
            out.append(f"| {num} | {lus} | {s} | {c} | {v} |")
    else:
        for num, lus, s, c, v in cliquets:
            out.append(f"{li}ADR {num} : lus={lus} suspects={s} cliquet={c} → {v}")
    out.append("")

    a_resserrer = [(num, s, c) for num, _, s, c, v in cliquets if v == "a-resserrer"]
    regressions = [(num, s, c) for num, _, s, c, v in cliquets if v == "regression"]

    if regressions:
        out.append(f"{h2}⚠ Régressions (un cas a été ajouté)")
        for num, s, c in regressions:
            out.append(
                f"{li}ADR {num} : {s} suspects pour un cliquet de {c}. À corriger sur la PR fautive."
            )
        out.append("")

    if a_resserrer:
        out.append(f"{h2}Cliquets à resserrer (la réalité fait mieux que la marge)")
        for num, s, c in a_resserrer:
            out.append(f"{li}ADR {num} : ramener le cliquet de {c} à {s}.")
        out.append("")
    else:
        out.append(f"{li}Aucun cliquet à resserrer : chaque marge colle à la réalité.")
        out.append("")

    if planchers:
        out += ["", f"{h2}Planchers (ce qu'on possède et qui ne redescend pas)"]
        for num, lus, mesure, plancher, verdict in planchers:
            fleche = "→ ok" if verdict == "ok" else f"→ {verdict}"
            out.append(f"{li}ADR {num} : lus={lus} mesure={mesure} plancher={plancher} {fleche}")
        out.append("")

    out.append(f"{h2}Loupes (vérifications « humaine », indicatif)")
    if loupes:
        for num, lus, n in loupes:
            out.append(f"{li}ADR {num} : {n} candidat(s) à revoir, sur {lus} unité(s) lue(s).")
    else:
        out.append(f"{li}Aucune loupe active.")
    if muets:
        out += ["", f"{h2}\u26a0 Verdicts que ce rapport n'a pas su lire"]
        for nom, rendu in muets:
            out.append(f"{li}{nom} : {rendu}")
        out.append(
            f"{li}Ces scripts ont été lancés ; leur sortie ne porte aucun verdict que ce"
            f" rapport sache lire. Un registre et un garde qui refuse de conclure sont"
            f" légitimement dans ce cas ; une ligne de verdict mal formée ne l'est pas."
        )

    return "\n".join(out) + "\n"


def resserrements(cliquets):
    """La liste (num, nouvelle_valeur) des cliquets à abaisser : c'est ce qu'un geste d'auto-calibration
    appliquerait dans les ADR."""
    return [(num, s) for num, lus, s, c, v in cliquets if v == "a-resserrer"]


def auto_test() -> int:
    """Le lancement s ancre, et se prouve par une sonde plutôt qu en relançant les dix-huit gardes."""
    import os
    import tempfile

    verifie, echecs = cas_d_auto_test()

    print("Auto-test du lancement des gardes (#4781) :")
    with tempfile.TemporaryDirectory() as brut:
        sonde = pathlib.Path(brut) / "sonde.py"
        sonde.write_text("import os\nprint(os.getcwd())\n", encoding="utf-8")

        # 1. Depuis le dépôt : la sonde doit déjà voir la racine, et non `scripts/adr`.
        verifie(
            "lancé du dépôt, le garde tourne à la racine",
            executer(sonde).strip(),
            str(RACINE_DEPOT),
        )

        # 2. Depuis AILLEURS : c est le cas qui a corrompu quatre ADR. Sans le premier, ce cas
        #    passerait au vert pour la mauvaise raison, le répertoire d essai étant peut-être le bon.
        ancien = os.getcwd()
        try:
            os.chdir(brut)
            verifie(
                "lancé d ailleurs, le garde tourne QUAND MÊME à la racine",
                executer(sonde).strip(),
                str(RACINE_DEPOT),
            )
            verifie(
                "et le répertoire d essai n était pas déjà la racine",
                os.getcwd() == str(RACINE_DEPOT),
                False,
            )
        finally:
            os.chdir(ancien)

    # ⟨le motif PARTAGE, et les deux cas que son arbitrage exige⟩ Ce rapport portait deux motifs
    # STRICTS qui enumeraient les champs dans l ordre. Le choix retenu est le LACHE, parce qu il etait
    # deja fait et deja eprouve dans `releve-les-planchers.py`, dont un cas dit « un champ INCONNU de
    # plus ne la fera pas perdre non plus ». Le danger du strict est qu il AGREGE : une ligne qu il
    # rate est un chiffre manquant, pas une erreur visible (#5830).
    futur = "ADR 4368 | lus=12 | source=git | suspects=0 | cliquet=0 | verdict=ok"
    verifie(
        "une ligne qui GAGNE un champ est toujours comptee",
        lambda: (champs_du_verdict(futur) or {}).get("numero"),
        "4368",
    )
    verifie(
        "et ses champs connus se lisent quand meme",
        lambda: (champs_du_verdict(futur) or {}).get("cliquet"),
        "0",
    )
    # Le CONTRASTE : une ligne MALFORMEE n est pas comptee. Sans lui, un motif qui prendrait tout
    # ferait compter de la prose comme un verdict, ce qui est le defaut inverse et aussi grave.
    verifie(
        "une ligne malformee n est PAS prise pour un verdict",
        lambda: champs_du_verdict("ADR 4368 sans barre ni verdict"),
        None,
    )
    # ⟨le champ `verdict` est EXIGE, et un cas le dit⟩ Sans lui, retirer cette exigence du motif ne
    # tuait que par un KeyError plus loin : un mutant tue par accident n est pas tenu par un temoin.
    verifie(
        "une ligne qui porte le mot-cle mais AUCUN verdict n est pas comptee",
        lambda: champs_du_verdict("ADR 4368 | lus=12 | suspects=0 | cliquet=0"),
        None,
    )
    # Et le `dispositif` est RENDU : c est lui qui separe les cliquets des planchers chez l appelant,
    # et sans ce cas son retrait ne tuait qu en levant.
    verifie(
        "le dispositif est rendu, et il separe les deux familles",
        lambda: (
            (champs_du_verdict("ADR 4368 | lus=1 | suspects=0 | cliquet=0 | verdict=ok") or {}).get(
                "dispositif"
            ),
            (champs_du_verdict("PLANCHER 4395 | mesure=1 | plancher=1 | verdict=ok") or {}).get(
                "dispositif"
            ),
        ),
        ("ADR", "PLANCHER"),
    )
    verifie(
        "ni une ligne de LOUPE, qui ne juge pas",
        lambda: champs_du_verdict("LOUPE 4712 | lus=12 | candidats=3"),
        None,
    )
    # Et le champ `lus` peut MANQUER : c est la forme que la docstring de `rapporte_plancher` donne en
    # exemple, et que les deux motifs stricts refusaient.
    verifie(
        "un plancher sans champ `lus` est compte, et son `lus` se lit « ? »",
        lambda: (
            champs_du_verdict("PLANCHER 4395 | mesure=4026 | plancher=4026 | verdict=ok") or {}
        ).get("lus", "?"),
        "?",
    )

    return echecs()


CONTRAT = {
    "geste": "rapport hebdomadaire : agrege les verdicts des cliquets et des loupes",
    "population": "les cliquets numerotes et les loupes de scripts/adr",
    "dispositif": "rapport",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/rapport.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    markdown = "--markdown" in sys.argv
    cliquets, planchers, loupes, muets = collecter()
    sys.stdout.write(rendre(cliquets, planchers, loupes, muets, markdown))
