(ns rules.dialogues
  (:require
   [assets.assets :as asset]
   [assets.texts :as texts]
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [engine.world :as world]
   [odoyle.rules :as o]
   [play-cljc.gl.core :as c]
   [play-cljc.instances :as instances]
   [play-cljc.transforms :as t]
   [rules.camera :as camera]
   [rules.dev.dev-only :as dev-only]
   [rules.input :as input]
   [rules.instanceable :as instanceable]
   [rules.pos2d :as pos2d]
   [rules.time :as time]))

(def dialogue-box-frag-shader
  {:precision "mediump float"
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
           (=float a (min (.a tex) 0.9))
           (= o_color (vec4 (.rgb tex) a)))}})

(s/def ::dialogue-instances* #(instance? #?(:clj clojure.lang.Atom :cljs Atom) %))

(defn init-asset [game db*]
  (let [asset-id  :id/berkelana
        raw-image (::instanceable/raw (get @db* asset-id))
        dialogue-instanced
        (c/compile game (-> (instances/->instanced-entity raw-image)
                            (assoc :fragment dialogue-box-frag-shader)))]
    (reset! (::dialogue-instances* game)
            {::raw raw-image
             ::instanced dialogue-instanced})))

(defn nine-patch [raw camera width height pos-x pos-y]
  (let [frame-index 48 frame-w 32  inset 5 ;; hardcoded for now, or forever
        frames-per-row (/ (:width raw) frame-w)
        frame-x (mod frame-index frames-per-row)
        frame-y (quot frame-index frames-per-row)
        crop-x (* frame-x frame-w)
        crop-y (* frame-y frame-w)
        raw  (t/invert raw camera)
        center (-> raw
                   (t/translate (+ inset pos-x) (+ inset pos-y))
                   (t/scale width height)
                   (t/crop (+ crop-x inset) (+ crop-y inset)
                           (- frame-w (* inset 2))
                           (- frame-w (* inset 2))))
        left   (-> raw
                   (t/translate (+ pos-x) (+ inset pos-y))
                   (t/scale inset height)
                   (t/crop crop-x (+ crop-y inset)
                           inset (- frame-w (* inset 2))))
        right  (-> raw
                   (t/translate (+ width inset pos-x) (+ inset pos-y))
                   (t/scale inset height)
                   (t/crop (+ crop-x frame-w (- inset)) (+ crop-y inset)
                           inset (- frame-w (* inset 2))))
        up     (-> raw
                   (t/translate (+ inset pos-x) (+ pos-y))
                   (t/scale width inset)
                   (t/crop (+ crop-x inset) crop-y
                           (- frame-w (* inset 2)) inset))
        down   (-> raw
                   (t/translate (+ inset pos-x) (+ height inset pos-y))
                   (t/scale width inset)
                   (t/crop (+ crop-x inset) (+ crop-y frame-w (- inset))
                           (- frame-w (* inset 2)) inset))
        l+u     (-> raw
                    (t/translate (+ pos-x) (+ pos-y))
                    (t/scale inset inset)
                    (t/crop crop-x crop-y inset inset))
        u+r     (-> raw
                    (t/translate (+ width inset pos-x) (+ pos-y))
                    (t/scale inset inset)
                    (t/crop (+ crop-x frame-w (- inset)) crop-y inset inset))
        r+d     (-> raw
                    (t/translate (+ width inset pos-x) (+ height inset pos-y))
                    (t/scale inset inset)
                    (t/crop (+ crop-x frame-w (- inset)) (+ crop-y frame-w (- inset)) inset inset))
        d+l    (-> raw
                   (t/translate (+ pos-x) (+ height inset pos-y))
                   (t/scale inset inset)
                   (t/crop crop-x (+ crop-y frame-w (- inset)) inset inset))]
    [center left l+u up u+r right r+d down d+l]))

(defn render [world game camera game-width game-height]
  (when (some? (::raw @(::dialogue-instances* game)))
    (doseq [text (->> (o/query-all world ::texts/texts-to-render)
                      (map :content)
                      (filter :dialogue?))]
      (let [{:keys [x y width]} text
            {::keys [raw instanced]} @(::dialogue-instances* game)
            height 18 pos-x -8 pos-y -6
            nine-patch (nine-patch raw camera width height (+ x pos-x) (+ y pos-y))]
        (c/render game (-> (reduce-kv instances/assoc instanced nine-patch)
                           (t/project game-width game-height)))))))

(def empty-text {:lines [] :x 0 :y 0})
(def text2
  {:lines ["I see now that the circumstances of one's birth"
           "are irrelevant"]
   :x 4 :y 110 :width 128
   :dialogue? true})

(def text
  {:lines ["It is what you do with the gift of life that"
           "determines who you are."]
   :x 4 :y 110 :width 128
   :dialogue? true})

(s/def ::delay-ms number?)
(s/def ::progress int?)

(world/system system
  {::world/init-fn
   (fn [world _game]
     (-> world
         (o/insert ::mew2 ::delay-ms 0)
         (o/insert ::mew2 ::texts/to-render text)
         (o/insert ::mew2 ::progress 0)))

   ::world/rules
   (o/ruleset
    {::prep-dialogue-box
     [:what
      [:id/berkelana ::asset/loaded? true]
      [::world/global ::world/game game]
      [::asset/global ::asset/db* db*]
      :then
      (when (nil? @(::dialogue-instances* game))
        (init-asset game db*))]

     ::reset-progress
     [:what
      [::time/now ::time/delta delta-time]
      [::input/space ::input/pressed-key ::input/keydown]
      :then
      (s-> session (o/insert ::mew2 ::progress 0))]

     ::progress-dialogue
     [:what
      [::time/now ::time/delta delta-time]
      [::camera/camera ::pos2d/pos2d pos]
      [::mew2 ::progress progress {:then false}]
      [::mew2 ::delay-ms delay-ms {:then false}]
      :then
      (let [cam-pos (update-vals pos #(* % 16)) ;; 16 from tile-size
            updated-text (-> text
                             (assoc :progress progress)
                             (update :x - (:x cam-pos))
                             (update :y - (:y cam-pos)))]
        (s-> session
             (dev-only/inspect-session progress pos)
             (o/insert ::mew2 ::texts/to-render updated-text)
             (cond-> (>  delay-ms 0) (o/insert ::mew2 ::delay-ms (- delay-ms delta-time))
                     (<= delay-ms 0) (-> (o/insert ::mew2 ::delay-ms 5)
                                         (o/insert ::mew2 ::progress (inc progress))))))]})

   ::world/render-fn render})