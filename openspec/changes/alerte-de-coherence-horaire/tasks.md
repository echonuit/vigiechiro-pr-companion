## 1. Le modèle dit le protocole (lot #4987)

- [ ] 1.1 Cas rouge : un démarrage 30 min AVANT le coucher ne doit signaler aucun défaut, il en signale un aujourd'hui
- [ ] 1.2 Cas rouge : un démarrage 30 min APRÈS le coucher doit signaler un avertissement, il n'en signale aucun aujourd'hui
- [ ] 1.3 Le niveau remplace les deux booléens `demarrageHorsNuit` et `arretHorsNuit` de `CoherenceHoraire`
- [ ] 1.4 La marge de 30 minutes devient une constante nommée du protocole, jamais un réglage
- [ ] 1.5 `CoherenceHoraire` porte la plage exigée et la plage effective
- [ ] 1.6 La javadoc cesse d'énoncer la règle inversée, qui est la prémisse d'où vient le défaut
- [ ] 1.7 Le troisième niveau se lit sur `CycleAcquisition`, jamais sur les horodatages des WAV
- [ ] 1.8 Cas des deux constats cumulés : une nuit à la fois trop courte et interrompue
- [ ] 1.9 Mutation de chaque prédicat : inverser une borne doit faire rougir son cas, et lui seul

## 2. Les deux surfaces rendent le même verdict (lot #4988)

- [ ] 2.1 L'écran de diagnostic affiche le niveau, la plage exigée et la plage effective
- [ ] 2.2 Le niveau « information » ne se présente pas comme un défaut, sans quoi le remède reproduit le mal
- [ ] 2.3 La commande `diagnostiquer` rend le même niveau, parité de l'ADR 0014
- [ ] 2.4 Cas qui interroge les deux surfaces sur la même nuit et compare : il doit rougir si une seule est corrigée
- [ ] 2.5 L'état indisponible reste distinct d'un verdict, sans coordonnées ni horaires
- [ ] 2.6 Capture de l'encart dans son état d'avertissement, sans quoi la capacité est livrée sans avoir été regardée

## 3. Les documents cessent de se contredire (lot #4989)

- [ ] 3.1 `docs/ecrans/diagnostic.md` cesse de présenter le respect du protocole comme le motif de l'alerte
- [ ] 3.2 `M-Diagnostic.md` et son SVG disent la règle du bon sens, et pourquoi la marge diurne est voulue
- [ ] 3.3 `E6` porte ce qui a été livré, à la place de son constat de manque
- [ ] 3.4 Recherche sur tout le corpus : plus aucune occurrence ne présente « démarrage avant le coucher » comme un défaut, le zéro confirmé motif par motif

## 4. La décision est écrite (passe 11 de la clôture)

- [ ] 4.1 ADR : le protocole est un plancher, et ce qu'on ne peut pas savoir ne se décide pas
- [ ] 4.2 L'ADR nomme son dispositif de vérification, et la mesure qui a écarté la détection par silences
