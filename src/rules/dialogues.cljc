(ns rules.dialogues
  (:require
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.transforms :as t]
   [rules.ubim :as ubim]))

(defn render [game world camera game-width game-height]
  (when-let [{:keys [x y]} (first (o/query-all world ::ubim/ubim-esse))]
    (let [frame-index 48
          asset-id :asset/berkelana
          {::spritesheet/keys [image frame-height frame-width]} (get @asset/db* asset-id)
          frames-per-row (/ (:width image) frame-width)
          frame-x (mod frame-index frames-per-row)
          frame-y (quot frame-index frames-per-row)
          crop-x (* frame-x frame-width)
          crop-y (* frame-y frame-height)]
      (c/render game (-> image
                         (t/project game-width game-height)
                         (t/invert camera)
                         (t/translate x (- y 16))
                         (t/scale frame-width frame-height)
                         (t/crop crop-x crop-y frame-width frame-height))))))