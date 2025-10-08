(ns engine.particle
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [iglu.core :as iglu]
   [play-cljc.gl.utils :as gl-utils])
  #?(:clj (:import [org.lwjgl BufferUtils])))

;; trying my best to follow
;; https://www.opengl-tutorial.org/intermediate-tutorials/billboards-particles/particles-instancing/#whats-the-point-

(def billboard-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-0.5 -0.5 0.0
    0.5 -0.5 0.0
    -0.5 0.5 0.0
    0.5 0.5 0.0]))

(def max-particle 1000000)
(defonce db* (volatile! {:particles []}))

(def bytes-per-float #?(:clj Float/BYTES   :cljs js/Float32Array.BYTES_PER_ELEMENT))
(def bytes-per-uint  #?(:clj Byte/BYTES :cljs js/Uint8Array.BYTES_PER_ELEMENT))
(def glsl-version #?(:clj "330" :cljs "300 es"))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_billboard vec3
                 a_particle_pos_size vec4
                 a_particle_color vec4}
   :outputs    '{v_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (=vec3 pos (+ (* a_billboard a_particle_pos_size.w) a_particle_pos_size.xyz))
           (= v_color a_particle_color)
           (= gl_Position (vec4 pos "1.0")))}})

(def fragment-shader
  {:precision  "mediump float"
   :inputs     '{v_color vec4}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color v_color))}})

(defn init [game]
  (let [vertex-source   (iglu/iglu->glsl (merge {:version glsl-version} vertex-shader))
        fragment-source (iglu/iglu->glsl (merge {:version glsl-version} fragment-shader))
        program         (gl-utils/create-program game vertex-source fragment-source)
        vao             (gl game #?(:clj genVertexArrays :cljs createVertexArray))]
    (gl game useProgram program)
    (gl game bindVertexArray vao)
    (vswap! db* assoc :program program :vao vao)
    (let [billboard-buffer (gl-utils/create-buffer game)
          loc (gl game getAttribLocation program "a_billboard")]
      (gl game bindBuffer (gl game ARRAY_BUFFER) billboard-buffer)
      (gl game bufferData (gl game ARRAY_BUFFER) billboard-data (gl game STATIC_DRAW))
      (vswap! db* assoc
              :billboard-buffer billboard-buffer
              :billboard-loc loc))

    (let [positions-buffer (gl-utils/create-buffer game)
          loc (gl game getAttribLocation program "a_particle_pos_size")]
      (gl game bindBuffer (gl game ARRAY_BUFFER) positions-buffer)
      (gl game bufferData (gl game ARRAY_BUFFER) (* max-particle 4 bytes-per-float) (gl game STREAM_DRAW))
      (vswap! db* assoc
              :positions-buffer positions-buffer
              :positions-loc loc))

    (let [colors-buffer (gl-utils/create-buffer game)
          loc (gl game getAttribLocation program "a_particle_color")]
      (gl game bindBuffer (gl game ARRAY_BUFFER) colors-buffer)
      (gl game bufferData (gl game ARRAY_BUFFER) (* max-particle 4 bytes-per-uint) (gl game STREAM_DRAW))
      (vswap! db* assoc
              :colors-buffer colors-buffer
              :colors-loc loc))))

(def particle-rate 1000)

(defn update-particle [db _world game]
  (let [{:keys [delta-time]} game
        new-particles-n (int (* (/ (min delta-time 16) 1000) particle-rate))
        new-particles (->> (range new-particles-n)
                           (mapv (fn [_i]
                                   {:pos [(- (rand 2) 1) (- (rand 2) 1) 0 0.04]
                                    :color (case (rand-int 4)
                                             0 [155 0 0 100]
                                             1 [0 155 0 100]
                                             2 [0 0 155 100]
                                             3 [155 155 0 100])
                                    :life-ms 1000})))]
    (update db :particles
            (fn [current-particles]
              (->> (concat current-particles new-particles)
                   (filter #(> (:life-ms %) 0))
                   (map (fn [particle]
                          (-> particle (update :life-ms - delta-time)))))))))

(def gl-error-map
  {1280 "GL_INVALID_ENUM"
   1281 "GL_INVALID_VALUE"
   1282 "GL_INVALID_OPERATION"
   1283 "GL_STACK_OVERFLOW"
   1284 "GL_STACK_UNDERFLOW"
   1285 "GL_OUT_OF_MEMORY"
   1286 "GL_INVALID_FRAMEBUFFER_OPERATION"
   0    "GL_NO_ERROR"})

#?(:clj
   ;; we can't use byte-array because lwjgl's glBufferData doesn't have the overload for byte[]
   (defn make-unit8-buffer [data]
     (let [buffer (BufferUtils/createByteBuffer (count data))]
       (.put buffer (byte-array (into [] (comp (map #(- % 128)) (map byte)) data)))
       (.flip buffer)
       buffer)))

(defn render [world game]
  (let [{:keys [program vao particles
                billboard-buffer billboard-loc
                positions-buffer positions-loc
                colors-buffer colors-loc]} (vswap! db* update-particle world game)
        positions-data (#?(:clj float-array :cljs #(js/Float32Array. %)) (mapcat :pos particles))
        particle-count (max (count particles) max-particle)
        colors-data    (#?(:clj make-unit8-buffer :cljs #(js/Uint8Array. %)) (mapcat :color particles))]
    (when (and billboard-buffer positions-buffer colors-buffer)
    ;;   (gl game getError) ;; to reset the error flag every frame
      (gl game useProgram program)
      (gl game bindVertexArray vao)

      (let [loc billboard-loc]
        (gl game bindBuffer (gl game ARRAY_BUFFER) billboard-buffer)
        (gl game enableVertexAttribArray loc)
        (gl game vertexAttribPointer loc 3 (gl game FLOAT) false 0 0)
        (gl game vertexAttribDivisor loc 0))

      (let [loc positions-loc]
        (gl game bindBuffer (gl game ARRAY_BUFFER) positions-buffer)
        (gl game bufferData (gl game ARRAY_BUFFER) positions-data (gl game STREAM_DRAW))
        (gl game enableVertexAttribArray loc)
        (gl game vertexAttribPointer loc 4 (gl game FLOAT) false 0 0)
        (gl game vertexAttribDivisor loc 1))

      (let [loc colors-loc]
        (gl game bindBuffer (gl game ARRAY_BUFFER) colors-buffer)
        (gl game bufferData (gl game ARRAY_BUFFER) colors-data (gl game STREAM_DRAW))
        (gl game enableVertexAttribArray loc)
        (gl game vertexAttribPointer loc 4 (gl game UNSIGNED_BYTE) true 0 0)
        (gl game vertexAttribDivisor loc 1))

      ;; (println "GL ERROR:" (gl-error-map (gl game getError)))
      (gl game drawArraysInstanced (gl game TRIANGLE_STRIP) 0 4 particle-count))))