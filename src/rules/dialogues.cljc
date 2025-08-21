(ns rules.dialogues
  (:require
   #?(:clj :cljs)
   [assets.assets :as asset]
   [assets.spritesheet :as spritesheet]
   [engine.context :as context]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.transforms :as t]
   [rules.ubim :as ubim]))

(def dialogue-box-frag-shader
  {:version "300 es"
   :precision "mediump float"
   :uniforms
   '{u_image sampler2D}
   :inputs
   '{v_tex_coord vec2}
   :outputs
   '{o_color vec4}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([] 
           (=vec4 tex (texture u_image v_tex_coord))
           (=float a (min (.a tex) 0.7))
           (= o_color (vec4 (.rgb tex) a)))}})

(defonce dialogue-asset* (atom nil))

(defn init-asset []
  (let [game      @context/game*
        asset-id  :asset/berkelana
        raw-image (-> (::spritesheet/raw-image (get @asset/db* asset-id))
                      (assoc :fragment dialogue-box-frag-shader))
        esse      (c/compile game raw-image)]
    (reset! dialogue-asset* esse)))

(def rules
  (o/ruleset
   {::prep-dialogue-box
    [:what
     [:asset/berkelana ::asset/loaded? true]
     :then
     (when (nil? @dialogue-asset*)
       (init-asset))]}))

(defn render [game world camera game-width game-height]
  (when (some? @dialogue-asset*)
    (let [frame-index 48 frame-width 32 frame-height frame-width
          {:keys [x y]} (first (o/query-all world ::ubim/ubim-esse))
          image @dialogue-asset*
          frames-per-row (/ (:width image) frame-width)
          frame-x (mod frame-index frames-per-row)
          frame-y (quot frame-index frames-per-row)
          crop-x (* frame-x frame-width)
          crop-y (* frame-y frame-height)]
      (c/render game (-> image
                         (t/project game-width game-height)
                         (t/invert camera)
                         (t/translate x (- y 32))
                         (t/scale frame-width frame-height)
                         (t/crop crop-x crop-y frame-width frame-height))))))
