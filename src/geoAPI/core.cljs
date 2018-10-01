(ns geoAPI.core
  (:require [cljs.core.async
             :as async
             :refer [chan put! take! >! <! pipe timeout close! alts! pipeline-async]]
            [cljs.core.async :refer-macros [go go-loop alt!]]
            [ajax.core :as http :refer [GET POST]]
            [oops.core :as obj]
            [clojure.string :as s]
            [cljs.pprint :refer [pprint]]
            ["dotenv" :as env]
            [clojure.repl :refer [source]]
            [defun.core :refer-macros [defun]]
            [utils.core :refer [strs->keys keys->strs map-idcs-range json-args->clj-keys =IO=>I=O= IO-ajax-GET-json xfxf<< xf!<<]]
            [geojson.index :refer [geoKeyMap]]))

(def base-url "https://raw.githubusercontent.com/loganpowell/census-geojson/master/GeoJSON")

(defn geo-error
  [res vin lev]
  (let [strs [(str "No GeoJSON found for " (name lev) "s at this scope")
              (str "in vintage: " vin)
              (str "at resolution: " res)
              (str "...:vintage {...:scope [...resolution]} sets available for this geography:")
              (if-let [vins  (get-in geoKeyMap [lev])]
                (map-over-keys #(get-in % [:scopes]) vins)
                "No sets available :(")]]
    (prn strs)
    ""))

(defn geo-url-builder
  "Composes a URL to call raw GeoJSON files hosted on Github"
  ([res vin lev] (geo-url-builder vin res lev nil))
  ([res vin lev st]
   (if (= st nil)
     (str (s/join "/" [base-url res vin (name lev)]) ".json")
     (str (s/join "/" [base-url res vin st (name lev)]) ".json"))))

(defn geo-scoper
  ([res vin lev USr]     (geo-scoper res vin lev USr nil nil))
  ([res vin lev USr STr] (geo-scoper res vin lev USr STr nil))
  ([res vin lev USr STr st]
   (let [STr? (not (= (some #(= res %) STr) nil))
         USr? (not (= (some #(= res %) USr) nil))
         st?  (not (= st nil))
         us?  (= st nil)]
     ;(prn (str "vin: " vin " res: " res " lev: " lev " USr: " USr " STr: " STr " st: " st)))))
     (cond
       (and st? STr?)                  (geo-url-builder res vin lev st) ;asks for state, state available
       (and us? USr?)                  (geo-url-builder res vin lev)    ;asks for us, us available
       (and (and st? USr?) (not STr?)) (geo-url-builder res vin lev)    ;asks for state, state unavailable, us available
       :else                           (geo-error res vin lev)))))


(defun geo-pattern-matcher
  "
  Takes a pattern of maps and triggers the URL builder accordingly
  "
  ([[nil _   _   _         _                    ]] nil)                                 ; no request for GeoJSON
  ([[res vin _   [lev _  ] nil                  ]] (geo-error res vin lev))             ; no valid geography
  ([[res vin _   [lev _  ] {:us USr :state nil }]] (geo-scoper res vin lev USr))        ; no state resolutions available, try :us
  ([[res vin nil [lev "*"] {:us USr :state _   }]] (geo-scoper res vin lev USr))        ; tries to get geo for all us
  ([[res vin "*" [lev "*"] {:us USr :state _   }]] (geo-scoper res vin lev USr))        ; tries to get geo for all us
  ([[res vin st  [lev _  ] {:us USr :state STr }]] (geo-scoper res vin lev USr STr st)) ; tries to get geo for a specific state
  ([[res vin nil [lev _  ] {:us nil :state _   }]] (geo-error res vin lev))             ; trying to get US level, when only state is available
  ([[res vin "*" [lev _  ] {:us nil :state _   }]] (geo-error res vin lev)))            ; trying to get US level, when only state is available

(defn geo-pattern-maker
  [{:keys [vintage geoResolution geoHierarchy]}]
  (let [level (last geoHierarchy)
        {:keys [state]} geoHierarchy
        geoScopes (get-in geoKeyMap [(key level) (keyword vintage) :scopes])
        pattern [geoResolution vintage state level geoScopes]]
    pattern))

(defn geo-url-composer
  [args]
  (-> (geo-pattern-maker args) geo-pattern-matcher))

;; Psedo
; create a map/index for :sourcePath -> vintage manipulation (e.g., CBP: [20)

;; Examples ====================================
(comment
  (geo-url-composer {:vintage       "2016"
                     :sourcePath    ["acs" "acs5"]
                     :geoHierarchy  {:state "01"
                                     :county "*"}
                     :geoResolution "500k"
                     :values        ["B01001_001E"]})
                    ; :statsKey      stats-key})
  (geo-url-composer {:vintage       "2016"
                     :sourcePath    ["acs" "acs5"]
                     :geoHierarchy  {:county "*"}
                     :geoResolution "500k"
                     :values        ["B01001_001E"]})

  (geo-url-composer {:vintage       "2016"
                     :sourcePath    ["acs" "acs5"]
                     :geoHierarchy  {:state "01"
                                     :county "001"
                                     :tract "*"}
                     :geoResolution "500k"
                     :values        ["B01001_001E"]})

  (geo-url-composer {:vintage       "2016"
                     :sourcePath    ["acs" "acs5"]
                     :geoHierarchy  {:state "01"
                                     :county "001"
                                     :someting-non-existant "*"}
                     :geoResolution "500k"
                     :values        ["B01001_001E"]})

  (geo-url-composer {:vintage       "2016"
                     :sourcePath    ["acs" "acs5"]
                     :geoHierarchy  {:state "*"
                                     :tract "*"}
                     :geoResolution "500k"
                     :values        ["B01001_001E"]}))

;; ===============================================

(defn getCensusGeoJSON
  "
  Library function, which takes a JSON object as input, constructs a call to get
  Github raw file and returns GeoJSON.
  "
  ([json-args cb]
   (let [args  (json-args->clj-keys json-args :geoHierarchy)
         url   (geo-url-composer args)
         =res= (chan 1)]
     (pprint args)
     (pprint (str url))
     (do ((=IO=>I=O= IO-ajax-GET-json) url =res=)
         (take! =res= #(cb %))))))

;; Examples  ========================================

(getCensusGeoJSON #js {"vintage"       "2016"
                       "sourcePath"    #js ["acs" "acs5"]
                       "geoHierarchy"  #js {"state" "12" "state legislative-district (upper chamber)" "*"}
                       "geoResolution" "500k"
                       "values"        #js ["B01001_001E" "NAME"]
                       "predicates"    #js {"B00001_001E" "0:30000"}}
               js/console.log)
