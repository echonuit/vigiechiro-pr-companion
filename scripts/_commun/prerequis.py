"""Le prerequis manquant LE PLUS PROFOND, et le geste qui le pose (issue #5774).

## Pourquoi ce module existe

Deux gardes OpenSpec refusaient en prescrivant `npm ci --prefix .github/openspec`. La ligne est
juste et elle ne suffit pas : sur un poste ou `node` et `npm` vivent sous un gestionnaire de version,
ils sont absents du PATH d un shell non interactif, et qui suit la prescription obtient
`npm: command not found`. Il en deduit un paquet absent, cause plausible, verifiable, et fausse.

Mesure du 2026-10-03 : **huit corps de demande** sur deux sessions ont qualifie ces refus
d « environnementaux, etrangers a ce diff », chacun apres avoir verifie qu ils rougissaient aussi sur
`main`. La verification etait juste et elle a dispense de chercher - « il refuse aussi sur main »
prouve que ce n est pas mon diff, et ne prouve pas que le poste ne peut pas y repondre.

L ADR 5407 avait deja nomme ce mecanisme un mois plus tot : « un garde qui refuse faute d un
prerequis rend un verdict qui ressemble a un defaut du changement en cours ». Ce module est ce qui
manquait pour que le refus ne ressemble plus a cela.

## Ce qu il cherche, et dans quel ordre

Le prerequis le plus profond d abord, parce que c est lui qui commande le geste :

1. le binaire epingle est-il la ? Sinon, **ce qui l installe** est-il la ?
2. le binaire est la : **son interprete** est-il la ?

Le second cas est celui qu aucun message ne couvrait. Le binaire pose est un script, son shebang
reclame un interprete, et cet interprete doit etre sur le PATH **au moment ou le garde tourne**, pas
seulement au moment de l installation. Une session a applique la premiere moitie de la reparation et
rate la seconde, exactement la.

## Pourquoi il LIT le shebang au lieu de nommer `node`

Nommer `node` serait retaper le motif du dispositif, ce que l ADR 5584 interdit apres cinq mesures
fausses en une journee. Le shebang EST la declaration de l interprete : le lire, c est importer le
motif. Et le jour ou l outil epingle changera d interprete, ce module le dira sans etre touche.

## Ce qu il ne fait pas, et c est une decision

**Il ne nomme aucun gestionnaire de version.** nvm, asdf, volta, fnm, un paquet systeme : la liste est
ouverte, et nommer l un d eux lierait un garde du depot a UN poste. Il dit ce qui manque ; la page de
la batterie locale dit comment l obtenir ici. C est le partage que l ADR 5407 pose entre ce que le
depot pose et ce qu il refuse en le disant.

**Il ne pose rien.** Poser est le geste de `prepare-l-environnement.py`, et son echec est l issue
#5775.
"""

from __future__ import annotations

import pathlib
import shutil

# Ce qui installe un outil epingle de ce depot. Une seule valeur, et elle est declaree ici plutot que
# dans la phrase de chaque garde : deux gardes qui la retapent divergeraient au premier changement.
INSTALLATEUR = "npm"

# La forme d un shebang qui delegue la resolution au PATH. C est celle que porte l outil epingle,
# `#!/usr/bin/env node`, et la seule qui nous interesse : un shebang en chemin absolu nomme son
# interprete sans passer par le PATH, donc son absence se verrait autrement.
_PAR_ENV = "#!/usr/bin/env "


def interprete_declare(epingle: pathlib.Path) -> str | None:
    """L interprete que le binaire reclame, LU dans son shebang, ou `None`.

    Rend `None` quand le fichier n est pas lisible, n a pas de shebang, ou porte un chemin absolu :
    dans ces trois cas il n y a pas d interprete a chercher sur le PATH, et inventer une reponse
    ferait refuser sur une cause imaginaire.
    """
    try:
        with epingle.open(encoding="utf-8", errors="replace") as fichier:
            premiere = fichier.readline()
    except OSError:
        return None
    if not premiere.startswith(_PAR_ENV):
        return None
    mots = premiere[len(_PAR_ENV) :].split()
    return mots[0] if mots else None


def manque_pour(epingle: pathlib.Path, pose: str, trouve=shutil.which) -> tuple[str, str] | None:
    """La cause et le geste du prerequis manquant, ou `None` si tout est la.

    `epingle` est le binaire attendu, `pose` la commande qui l installe. `trouve` est injecte pour
    que les cas puissent decrire un PATH sans en fabriquer un : un cas qui modifierait le PATH du
    processus eprouverait l environnement du harnais et non ce module.

    Rend un couple `(cause, geste)` et non une phrase : c est la forme que `refuse` attend, et les
    deux champs sont de nature differente - pourquoi je ne peux pas conclure, et ce qu il faut faire.
    """
    if not epingle.exists():
        if trouve(INSTALLATEUR) is None:
            return (
                (
                    f"« {INSTALLATEUR} » est introuvable sur le PATH, donc {epingle} ne peut pas"
                    " etre pose : ce refus ne parle pas de votre diff"
                ),
                f"mettez « {INSTALLATEUR} » sur le PATH, puis lancez : {pose}",
            )
        return (
            (
                f"{epingle} est absent, et ce garde compare a l outil EPINGLE jamais a celui du"
                " PATH, qui peut etre d une autre version"
            ),
            pose,
        )

    interprete = interprete_declare(epingle)
    if interprete is not None and trouve(interprete) is None:
        return (
            (
                f"« {interprete} » est introuvable sur le PATH, et {epingle} est un script qui le"
                " reclame dans son shebang : l outil est POSE, c est son interprete qui manque."
                " Ce refus ne parle pas de votre diff"
            ),
            f"mettez « {interprete} » sur le PATH ; l outil epingle, lui, est deja installe",
        )
    return None


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de ce module, joues par les auto-tests des DEUX gardes qui l appellent.

    Un gage qu aucun harnais n appelle est inerte, et `verifie_gages_joues.py` le refuse (ADR 5483).
    """
    import tempfile

    bac = pathlib.Path(tempfile.mkdtemp())
    absent = bac / "absent"

    pose_en_node = bac / "en-node"
    pose_en_node.write_text("#!/usr/bin/env node\n", encoding="utf-8")

    pose_en_bun = bac / "en-bun"
    pose_en_bun.write_text("#!/usr/bin/env bun\n", encoding="utf-8")

    pose_en_absolu = bac / "en-absolu"
    pose_en_absolu.write_text("#!/bin/sh\n", encoding="utf-8")

    sans_shebang = bac / "sans-shebang"
    sans_shebang.write_text("console.log(1)\n", encoding="utf-8")

    vide = bac / "vide"
    vide.write_text("", encoding="utf-8")

    tout = lambda _: "/usr/bin/quelque-chose"
    rien = lambda _: None
    sauf = lambda manquant: lambda nom: None if nom == manquant else "/usr/bin/x"

    cas: list[tuple[str, bool]] = []

    # Le binaire est la, son interprete aussi : rien a dire.
    cas.append(("tout est pose, rien n est rendu", manque_pour(pose_en_node, "p", tout) is None))

    # Le cas que les deux sessions ont rencontre, et qu aucun message ne couvrait.
    manque_node = manque_pour(pose_en_node, "p", sauf("node"))
    cas.append(("l interprete absent est VU alors que l outil est pose", manque_node is not None))
    cas.append(
        (
            "et sa cause NOMME l interprete",
            manque_node is not None and "node" in manque_node[0],
        )
    )
    cas.append(
        (
            "et son geste ne renvoie PAS a la commande d installation",
            manque_node is not None and "p" not in manque_node[1].split(),
        )
    )

    # LE cas qui discrimine : l interprete est LU dans le shebang, pas ecrit en dur.
    manque_bun = manque_pour(pose_en_bun, "p", sauf("bun"))
    cas.append(
        (
            "un AUTRE interprete est nomme, donc le shebang est bien LU",
            manque_bun is not None and "bun" in manque_bun[0] and "node" not in manque_bun[0],
        )
    )
    cas.append(
        (
            "et le meme fichier en bun ne rend rien quand bun est la",
            manque_pour(pose_en_bun, "p", sauf("node")) is None,
        )
    )

    # Les trois formes sans interprete a chercher : aucune ne doit refuser.
    cas.append(
        (
            "un shebang en chemin ABSOLU ne fait pas refuser",
            manque_pour(pose_en_absolu, "p", rien) is None,
        )
    )
    cas.append(
        (
            "un fichier SANS shebang ne fait pas refuser",
            manque_pour(sans_shebang, "p", rien) is None,
        )
    )
    cas.append(("un fichier VIDE ne fait pas refuser", manque_pour(vide, "p", rien) is None))

    # Le binaire absent, selon que ce qui l installe est la ou non.
    manque_outil = manque_pour(absent, "npm ci --prefix x", sauf("node"))
    cas.append(("l outil absent est vu", manque_outil is not None))
    cas.append(
        (
            "et son geste est la commande d installation",
            manque_outil is not None and manque_outil[1] == "npm ci --prefix x",
        )
    )

    manque_npm = manque_pour(absent, "npm ci --prefix x", rien)
    cas.append(
        (
            "l INSTALLATEUR absent est nomme avant la commande qu il ne peut pas jouer",
            manque_npm is not None and INSTALLATEUR in manque_npm[0],
        )
    )
    cas.append(
        (
            "et son geste porte les DEUX gestes, le PATH puis l installation",
            manque_npm is not None
            and INSTALLATEUR in manque_npm[1]
            and "npm ci --prefix x" in manque_npm[1],
        )
    )
    cas.append(
        (
            "les deux causes d un outil absent DIFFERENT selon que l installateur est la",
            manque_outil is not None
            and manque_npm is not None
            and manque_outil[0] != manque_npm[0],
        )
    )

    # La decision de ne nommer aucun gestionnaire de version se verifie, elle ne se promet pas.
    rendus = " ".join(
        champ
        for couple in (manque_node, manque_bun, manque_outil, manque_npm)
        if couple is not None
        for champ in couple
    )
    cas.append(
        (
            "AUCUN gestionnaire de version n est nomme",
            not any(g in rendus for g in ("nvm", "asdf", "volta", "fnm", "nodenv")),
        )
    )

    # Un refus qui ne parle pas du diff le DIT, ce que l ADR 5407 demande.
    cas.append(
        (
            "les deux causes qui tiennent au POSTE disent qu elles ne parlent pas du diff",
            manque_node is not None
            and manque_npm is not None
            and "votre diff" in manque_node[0]
            and "votre diff" in manque_npm[0],
        )
    )

    shutil.rmtree(bac, ignore_errors=True)
    return cas
