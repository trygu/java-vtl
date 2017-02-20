angular.module('vtl', ['ui.codemirror', 'angular.filter'])
    .controller('ExecutionController', ['$scope', '$http', '$q', function ($scope, $http, $q) {
        "use strict";
        $scope.expression = "a := get(\"1104\")";

        $scope.editorOptions = {
            lineWrapping: true,
            lineNumbers: true,
            mode: "vtl",
            gutters: ["CodeMirror-lint-markers"],
            lint: {
                async: true
            }
        };

        $scope.options = {
            limitTuple: 5
        };

        $scope.variables = [];

        $scope.datasets = {};

        $scope.executionError = null;

        $scope.fetchData = function (dataset) {
            if (angular.isUndefined($scope.datasets[dataset])) {
                $scope.datasets[dataset] = {};
            }

            var data = $http.get("/dataset/" + dataset + "/data");

            data.then(function (response) {
                $scope.datasets[dataset]["data"] = response.data;
            },function (response) {
                $scope.datasets[dataset]["error"] = response.data;
            });
        };

        $scope.remove = function (dataset) {
            var data = $http.delete("/dataset/" + dataset);
            data.then(function () {
                delete $scope.datasets[dataset];
            });
        };

        $scope.roleOrder = function (variable) {
            switch(variable.role) {
                case "IDENTIFIER":
                    return 1;
                case "MEASURE":
                    return 2;
                case "ATTRIBUTE":
                    return 3;
                default:
                    return 4;
            }
        };

        $scope.execute = function () {
            // Simple GET request example:
            $http({
                data: $scope.expression,
                method: 'POST',
                url: '/execute'
            }).then(function successCallback(response) {

                $scope.executionError = null;
                var datasets = response.data;
                var promises = {};

                $scope.variables = datasets;

                for (var i in datasets) {
                    var dataset = datasets[i];

                    if (angular.isUndefined($scope.datasets[dataset])) {
                        $scope.datasets[dataset] = {};
                    }
                    var promise = $http.get("/dataset/" + dataset + "/structure");
                    promises[dataset] = promise.then(function (response) {
                        return {variables: response.data.dataStructure};
                    },function (response) {
                        return { error: response.data};
                    });
                }

                $q.all(promises).then(function (result) {
                    $scope.datasets = result;
                })

            }, function errorCallback(response) {
                $scope.executionError = response.data;
                // called asynchronously if an error occurs
                // or server returns response with an error status.
            });
        }
    }]);