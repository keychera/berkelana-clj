(ns engine.particle
  (:require
   #?(:clj  [play-cljc.macros-java :refer [gl]]
      :cljs [play-cljc.macros-js :refer-macros [gl]])
   [iglu.core :as iglu]
   [play-cljc.gl.utils :as gl-utils]))

;; trying my best to follow
;; https://www.opengl-tutorial.org/intermediate-tutorials/billboards-particles/particles-instancing/#whats-the-point-

(def billboard-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-0.5 -0.5 0.0
    0.5 -0.5 0.0
    -0.5 0.5 0.0
    0.5 0.5 0.0]))

(def max-particle 4)
;; (def positions-data
;;   (#?(:clj float-array :cljs #(js/Float32Array. %))
;;    (* max-particle 4)))

;; (def colors-data
;;   (#?(:clj int-array   :cljs #(js/Uint8Array. %))
;;    (* max-particle 4)))

(def positions-data
  (#?(:clj float-array :cljs #(js/Float32Array. %))
   [-0.5 -0.5 0.0 0.1   ;; bottom-left
    0.5 -0.5 0.0 0.1   ;; bottom-right
    -0.5  0.5 0.0 0.1   ;; top-left
    0.5  0.5 0.0 0.1])) ;; top-right

;; Colors: RGBA per vertex (0–255)
(def colors-data
  (#?(:clj int-array :cljs #(js/Uint8Array. %))
   [255 0 0 255    ;; red
    0 255 0 255    ;; green
    0 0 255 255    ;; blue
    255 255 0 255])) ;; yellow

(def db* (volatile! {}))

(def bytes-per-float #?(:clj Float/BYTES :cljs js/Float32Array.BYTES_PER_ELEMENT))
(def bytes-per-ubyte #?(:clj Byte/BYTES :cljs js/Uint8Array.BYTES_PER_ELEMENT))
(def glsl-version #?(:clj "330" :cljs "300 es"))

(def vertex-shader
  {:precision  "mediump float"
   :inputs     '{a_billboard vec3
                 a_particle_pos_size vec4
                 a_particle_color vec4}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([]
           (=vec3 pos (+ (* a_billboard a_particle_pos_size.w) a_particle_pos_size.xyz))
           (= o_color a_particle_color)
           (= gl_Position (vec4 pos "1.0")))}})

(def fragment-shader
  {:precision  "mediump float"
   :inputs     '{a_color vec4}
   :outputs    '{o_color vec4}
   :signatures '{main ([] void)}
   :functions
   '{main ([] (= o_color (vec4 "1.0" "0.0" "0.0" "1.0")))}})

(defn init [game]
  (let [vertex-source (iglu/iglu->glsl (merge {:version glsl-version} vertex-shader))
        fragment-source (iglu/iglu->glsl (merge {:version glsl-version} fragment-shader))
        program (gl-utils/create-program game vertex-source fragment-source)]
    (vswap! db* assoc :program program)
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
      (gl game bufferData (gl game ARRAY_BUFFER) positions-data (gl game STREAM_DRAW))
      (vswap! db* assoc
              :positions-buffer positions-buffer
              :positions-loc loc))

    (let [colors-buffer (gl-utils/create-buffer game)
          loc (gl game getAttribLocation program "a_particle_color")]
      (gl game bindBuffer (gl game ARRAY_BUFFER) colors-buffer)
      (gl game bufferData (gl game ARRAY_BUFFER) colors-data (gl game STREAM_DRAW))
      (vswap! db* assoc
              :colors-buffer colors-buffer
              :colors-loc loc))))

(defn render [_world game]
  (let [{:keys [program
                billboard-buffer billboard-loc
                positions-buffer positions-loc
                colors-buffer colors-loc]} @db*]
    (when (and billboard-buffer positions-buffer colors-buffer)
      ;; billboard
      (let [loc billboard-loc]
        (gl game enableVertexAttribArray loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) billboard-buffer)
        (gl game vertexAttribPointer loc 3 (gl game FLOAT) (gl game FALSE) 0 0)
        (gl game vertexAttribDivisor loc 0))

      (let [loc positions-loc]
        (gl game enableVertexAttribArray loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) positions-buffer)
        (gl game bufferData (gl game ARRAY_BUFFER) (* max-particle 4 bytes-per-float) (gl game STREAM_DRAW))
        (gl game bufferSubData (gl game ARRAY_BUFFER) 0 positions-data)
        (gl game vertexAttribPointer loc 4 (gl game FLOAT) (gl game FALSE) 0 0)
        (gl game vertexAttribDivisor loc 1))

      (let [loc colors-loc]
        (gl game enableVertexAttribArray loc)
        (gl game bindBuffer (gl game ARRAY_BUFFER) colors-buffer)
        (gl game bufferData (gl game ARRAY_BUFFER) (* max-particle 4 bytes-per-ubyte) (gl game STREAM_DRAW))
        (gl game bufferSubData (gl game ARRAY_BUFFER) 0 colors-data)
        (gl game vertexAttribPointer loc 4 (gl game UNSIGNED_BYTE) (gl game TRUE) 0 0)
        (gl game vertexAttribDivisor loc 1))

      (gl game useProgram program)
      (gl game drawArraysInstanced (gl game TRIANGLE_STRIP) 0 4 max-particle))))