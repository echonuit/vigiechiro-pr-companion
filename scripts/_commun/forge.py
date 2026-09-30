"""L appel a la forge, et le REFUS quand elle se tait.

Quatre points d entree lisaient la forge et definissaient chacun leur appel. Mesure du 2026-09-29,
par empreinte des corps normalises : deux etaient strictement identiques, la troisieme plus courte de
176 caracteres, la quatrieme une variante. Le corps de #5544 demandait si la troisieme refusait
**moins** ou **autrement**.

**Elle ne refusait pas moins : elle ne refusait pas.** Voici ce que chacune rendait :

    site                          gh absent   gh NON ZERO                gh vide
    loupe-4992, loupe-5539        REFUS, 2    REFUS, 2                   rend le vide
    releve-les-bancs-instables    rend ""     rend stdout, code IGNORE   rend ""
    verifie_chantier_de_l_issue   REFUS, 2    REFUS, 2 avec le numero    rend ""

## Pourquoi le refus n est PAS une option de cet appel

C etait le premier dessin de ce module, et une mesure l a refuse. Ce que les appelants FONT du vide
decide, et non la longueur de leur corps :

    loupe-4992, ligne 119   json.loads(interroge(...))
      json.loads("") leve JSONDecodeError, donc sans le refus la loupe PLANTE au lieu de refuser.
      Une trace de pile ne dit pas lequel de ses controles a rougi, ce que l ADR 4918 refuse.

    releve-les-bancs-instables   brut.splitlines()
      "".splitlines() rend [], donc la boucle ne tourne pas et le releve conclut « aucun banc
      instable ». Un faux negatif SILENCIEUX, et rien dans ce fichier ne distinguait « rien lu »
      de « rien trouve ».

Trois appelants dependent donc du refus - sans lui ils plantent - et le quatrieme est
silencieusement faux sans lui. Une option « refuser ou non » offrirait exactement le reglage qui
produit le defaut que ce lot corrige, et le premier appelant qui la choisirait par commodite
rejouerait le bug. **Ce qui reste au choix de l appelant est ce qu il fait APRES avoir recu une
reponse, pas s il accepte de ne pas en avoir.**

## Ce que ce module change du releve, et ce n est pas neutre

`releve-les-bancs-instables.py` est un RELEVE : il signale sans bloquer, c est son regime. Lui
imposer le refus le fait sortir non nul la ou il rendait zero en silence. **C est le but** - « je n ai
pas pu lire » n est pas « rien a signaler » - et c est un changement observable, decide plutot que
glisse.

## Pourquoi ici et non dans `.github/scripts/_forge.py`

`_forge.py` existe et porte deja la forge, mais cote CI. La raison de ne pas y mettre cet appel n est
pas la direction des imports, c est la COUCHE : `_forge.py` offre `liste_issues` et `vue_issue`, des
fonctions qui savent ce qu est une **issue**. Cet appel-ci ne sait rien du domaine - il sait lancer
`gh`, lire un code, et distinguer trois silences. Les melanger est precisement ce qui rend une
duplication tentante : on recopie douze lignes plutot que d importer un module qui parle d issues
quand on n en veut pas. Formulation due a `vigiechiro-pr-companion-56`, meilleure que la mienne.

La mesure la soutient : les quatre points d entree n ont pas tous besoin d issues, mais tous ont
besoin du meme refus.

Et la direction des imports ne s y oppose pas, contrairement a ce que le corps de #5544 affirmait :
`verifie_butoirs.py` et `temoins_de_ci_non_decoratifs.py` importent deja `scripts/_commun` en ajoutant
`scripts` au chemin de recherche. « Les deux paquets ne se voient pas » etait faux, et c est ce qui
permet aux quatre de partager au lieu d en laisser deux copies.

## Ou vivent les cas de ce module

Dans `verifie_grammaire()` plus bas, joues par l auto-test de `scripts/adr/loupe-4992-lots-sans-critere.py`.
UN seul joueur, et non les quatre : un cas rouge doit nommer le controle qui a rougi (ADR 4918), et
quatre consommateurs rapportant le meme echec le nomment quatre fois. Le choix de celui-la n est pas
arbitraire - il portait deja le cas qui exerce l APPEL et non le verdict, apres qu une mutation ait
montre que retirer le refus laissait son auto-test vert (ADR 4331).

Chacun des quatre garde en plus **son** cas qui traverse son propre chemin : ce module prouve que
l appel refuse, pas que chaque appelant passe bien par lui.
"""

from __future__ import annotations

import shutil
import subprocess

from _commun import refuse

GESTE_ABSENT = "Installez « gh », ou lancez ce dispositif la ou il est disponible."
GESTE_MUETTE = "Verifiez « gh auth status » et le reseau, puis relancez."


def interroge(arguments: list[str], *, quoi: str) -> str:
    """La sortie de `gh`, ou un REFUS. Le code de retour est TOUJOURS lu.

    `quoi` nomme ce qui n a pas pu etre lu, et entre dans la cause du refus. Sans lui, les quatre
    appelants rendraient le meme message et le lecteur ne saurait pas lequel a renonce - c est le
    defaut que `verifie_chantier_de_l_issue.py` evitait seul, en nommant le numero de l issue.

    Il se lit comme un COMPLEMENT : « ce dispositif ne peut pas lire {quoi} ». La forme evite de
    s accorder avec lui, ce qui laisse passer aussi bien « le lot #12 » que « les tirages de
    lint.yml ». Le premier gabarit disait « {quoi} n a pas pu etre lu » et rendait « les tirages
    [...] n a pas pu etre lu ».
    """
    if shutil.which("gh") is None:
        refuse(f"« gh » est absent, donc ce dispositif ne peut pas lire {quoi}.", GESTE_ABSENT)
    rendu = subprocess.run(["gh", *arguments], capture_output=True, text=True, check=False)
    if rendu.returncode != 0:
        refuse(f"la forge n a pas repondu : ce dispositif n a pas pu lire {quoi}.", GESTE_MUETTE)
    return rendu.stdout


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les trois cas de cet appel, sur un PATH fabrique plutot que sur la vraie forge.

    Les trois que le critere de #5544 exige : `gh` absent, `gh` qui rend non zero, `gh` qui rend du
    vide. Le troisieme est celui qu on oublie, et c est le seul des trois qui doit PASSER : un appel
    qui refuserait sur une reponse vide confondrait « la forge se tait » avec « la forge dit qu il n y
    a rien », ce qui est l erreur symetrique de celle que ce module corrige.
    """
    import os
    import pathlib
    import tempfile

    ou = pathlib.Path(tempfile.mkdtemp(prefix="forge-cas-"))

    def avec_un_faux_gh(corps: str, arguments: list[str]) -> tuple[int | None, str]:
        """Joue `interroge` avec un `gh` fabrique, et rend (code de sortie, sortie rendue)."""
        faux = ou / "gh"
        faux.write_text(corps, encoding="utf-8")
        faux.chmod(0o755)
        avant = os.environ.get("PATH", "")
        os.environ["PATH"] = str(ou)
        try:
            return None, interroge(arguments, quoi="un cas")
        except SystemExit as sortie:
            return sortie.code, ""
        finally:
            os.environ["PATH"] = avant

    def sans_gh() -> int | None:
        vide = pathlib.Path(tempfile.mkdtemp(prefix="forge-sans-gh-"))
        avant = os.environ.get("PATH", "")
        os.environ["PATH"] = str(vide)
        try:
            interroge(["issue", "list"], quoi="un cas")
        except SystemExit as sortie:
            return sortie.code
        finally:
            os.environ["PATH"] = avant
        return None

    code_absent = sans_gh()
    code_non_zero, _ = avec_un_faux_gh("#!/bin/sh\nexit 1\n", ["issue", "list"])
    code_vide, rendu_vide = avec_un_faux_gh("#!/bin/sh\nexit 0\n", ["issue", "list"])

    return [
        ("« gh » absent fait REFUSER en 2", code_absent == 2),
        ("un code NON ZERO fait REFUSER en 2, il n est pas avale", code_non_zero == 2),
        ("une reponse VIDE ne refuse pas : la forge a repondu", code_vide is None),
        ("et le vide est rendu tel quel, a l appelant de decider", rendu_vide == ""),
    ]
