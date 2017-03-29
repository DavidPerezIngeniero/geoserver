/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.rest;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.rest.ResourceNotFoundException;
import org.geoserver.rest.RestException;
import org.geoserver.rest.converters.XStreamMessageConverter;
import org.geoserver.rest.wrapper.RestWrapper;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@RestController
@ControllerAdvice
@RequestMapping(path = "/restng/workspaces/{workspace}")
public class CoverageController extends CatalogController {

    private static final Logger LOGGER = Logging.getLogger(CoverageController.class);

    @Autowired
    public CoverageController(Catalog catalog) {
        super(catalog);
    }

    @GetMapping(path = "coveragestores/{store}/coverages", produces = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE})
    public RestWrapper<CoverageInfo> getCoverages(@PathVariable(name = "workspace") String workspaceName,
                                                  @PathVariable(name = "store") String storeName) {
        // find the coverage store
        CoverageStoreInfo coverageStore = getExistingCoverageStore(workspaceName, storeName);
        // get the store configured coverages
        List<CoverageInfo> coverages = catalog.getCoveragesByCoverageStore(coverageStore);
        return wrapList(coverages, CoverageInfo.class);
    }

    @GetMapping(path = "coverages", produces = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE,
            TEXT_JSON})
    public RestWrapper<CoverageInfo> getCoverages(@PathVariable(name = "workspace") String workspaceName) {
        // get the workspace name space
        NamespaceInfo nameSpace = catalog.getNamespaceByPrefix(workspaceName);
        if (nameSpace == null) {
            // could not find the namespace associated with the desired workspace
            throw new ResourceNotFoundException(String.format(
                    "Name space not found for workspace '%s'.", workspaceName));
        }
        // get all the coverages of the workspace \ name space
        List<CoverageInfo> coverages = catalog.getCoveragesByNamespace(nameSpace);
        return wrapList(coverages, CoverageInfo.class);
    }

    @GetMapping(path = "coveragestores/{store}/coverages/{coverage}", produces = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE,
            TEXT_JSON})
    public RestWrapper<CoverageInfo> getCoverage(@PathVariable(name = "workspace") String workspaceName,
                                                 @PathVariable(name = "store") String storeName,
                                                 @PathVariable(name = "coverage") String coverageName) {
        CoverageStoreInfo coverageStore = getExistingCoverageStore(workspaceName, storeName);
        List<CoverageInfo> coverages = catalog.getCoveragesByCoverageStore(coverageStore);
        Optional<CoverageInfo> optCoverage = coverages.stream()
                .filter(ci -> coverageName.equals(ci.getName())).findFirst();
        if (!optCoverage.isPresent()) {
            throw new ResourceNotFoundException(String.format(
                    "No such coverage: %s,%s,%s", workspaceName, storeName, coverageName));
        }
        CoverageInfo coverage = optCoverage.get();
        checkCoverageExists(coverage, workspaceName, coverageName);
        return wrapObject(coverage, CoverageInfo.class);
    }

    @GetMapping(path = "coverages/{coverage}", produces = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_HTML_VALUE,
            TEXT_JSON})
    public RestWrapper<CoverageInfo> getCoverage(@PathVariable(name = "workspace") String workspaceName,
                                                 @PathVariable(name = "coverage") String coverageName) {
        // get the workspace name space
        NamespaceInfo nameSpace = catalog.getNamespaceByPrefix(workspaceName);
        if (nameSpace == null) {
            // could not find the namespace associated with the desired workspace
            throw new ResourceNotFoundException(String.format(
                    "Name space not found for workspace '%s'.", workspaceName));
        }
        CoverageInfo coverage = catalog.getCoverageByName(nameSpace, coverageName);
        checkCoverageExists(coverage, workspaceName, coverageName);
        return wrapObject(coverage, CoverageInfo.class);
    }

    @PostMapping(path = "coveragestores/{store}/coverages", consumes = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> postCoverage(@RequestBody CoverageInfo coverage,
                                               @PathVariable(name = "workspace") String workspaceName,
                                               @PathVariable(name = "store") String storeName,
                                               UriComponentsBuilder builder) throws Exception {
        String coverageName = handleObjectPost(coverage, workspaceName, storeName);
        UriComponents uriComponents = builder.path("/workspaces/{workspaceName}/coveragestores/{store}/coverages/{coverage}")
                .buildAndExpand(workspaceName, storeName, coverageName);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(uriComponents.toUri());
        return new ResponseEntity<>(coverageName, headers, HttpStatus.CREATED);
    }

    @PostMapping(path = "coverages", consumes = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<String> postCoverage(@RequestBody CoverageInfo coverage,
                                               @PathVariable(name = "workspace") String workspaceName,
                                               UriComponentsBuilder builder) throws Exception {
        String coverageName = handleObjectPost(coverage, workspaceName, null);
        UriComponents uriComponents = builder.path("/workspaces/{workspaceName}/coverages/{coverage}")
                .buildAndExpand(workspaceName, coverageName);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(uriComponents.toUri());
        return new ResponseEntity<>(coverageName, headers, HttpStatus.CREATED);
    }

    @PutMapping(path = "coveragestores/{store}/coverages/{coverage}", consumes = {
            MediaType.TEXT_XML_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_JSON_VALUE})
    public void putCoverage(@RequestBody CoverageInfo coverage,
                            @PathVariable(name = "workspace") String workspaceName,
                            @PathVariable(name = "store") String storeName,
                            @PathVariable(name = "coverage") String coverageName,
                            @RequestParam(name = "calculate", required = false) String calculate) throws Exception {
        CoverageStoreInfo cs = catalog.getCoverageStoreByName(workspaceName, storeName);
        CoverageInfo original = catalog.getCoverageByCoverageStore(cs, coverageName);
        checkCoverageExists(original, workspaceName, coverageName);
        calculateOptionalFields(coverage, original, calculate);
        new CatalogBuilder(catalog).updateCoverage(original, coverage);
        catalog.validate(original, false).throwIfInvalid();
        catalog.save(original);
        catalog.getResourcePool().clear(original.getStore());
        LOGGER.info("PUT coverage " + storeName + "," + coverage);
    }


    @DeleteMapping(path = "coveragestores/{store}/coverages/{coverage}")
    protected void deleteCoverage(@PathVariable(name = "workspace") String workspaceName,
                                  @PathVariable(name = "store") String storeName,
                                  @PathVariable(name = "coverage") String coverageName,
                                  @RequestParam(name = "recurse", defaultValue = "false") boolean recurse) {
        CoverageStoreInfo ds = catalog.getCoverageStoreByName(workspaceName, storeName);
        CoverageInfo c = catalog.getCoverageByCoverageStore(ds, coverageName);
        if (c == null) {
            throw new RestException(String.format(
                    "Coverage '%s' not found.", coverageName), HttpStatus.NOT_FOUND);
        }
        List<LayerInfo> layers = catalog.getLayers(c);
        if (recurse) {
            //by recurse we clear out all the layers that public this resource
            for (LayerInfo l : layers) {
                catalog.remove(l);
                LOGGER.info("DELETE layer " + l.getName());
            }
        } else {
            if (!layers.isEmpty()) {
                throw new RestException("coverage referenced by layer(s)", HttpStatus.FORBIDDEN);
            }
        }
        catalog.remove(c);
        catalog.getResourcePool().clear(c.getStore());
        LOGGER.info("DELETE coverage " + storeName + "," + coverageName);
    }

    /**
     * If the coverage doesn't exists throws a REST exception with HTTP 404 code.
     */
    private void checkCoverageExists(CoverageInfo coverage, String workspaceName, String coverageName) {
        if (coverage == null) {
            throw new ResourceNotFoundException(String.format(
                    "No such coverage: %s,%s", workspaceName, coverageName));
        }
    }

    /**
     * Helper method that find a store based on the workspace name and store name.
     */
    private CoverageStoreInfo getExistingCoverageStore(String workspaceName, String storeName) {
        CoverageStoreInfo original = catalog.getCoverageStoreByName(workspaceName, storeName);
        if (original == null) {
            throw new ResourceNotFoundException(
                    "No such coverage store: " + workspaceName + "," + storeName);
        }
        return original;
    }

    /**
     * Helper method that handles the POST of a coverage. This handles both the cases
     * when the store is provided and when the store is not provided.
     */
    private String handleObjectPost(CoverageInfo coverage, String workspace, String coverageStoreName) throws Exception {
        if (coverage.getStore() == null) {
            CoverageStoreInfo ds = catalog.getCoverageStoreByName(workspace, coverageStoreName);
            coverage.setStore(ds);
        }
        final boolean isNew = isNewCoverage(coverage);
        String nativeCoverageName = coverage.getNativeCoverageName();
        if (nativeCoverageName == null) {
            nativeCoverageName = coverage.getNativeName();
        }
        CatalogBuilder builder = new CatalogBuilder(catalog);
        CoverageStoreInfo store = coverage.getStore();
        builder.setStore(store);

        // We handle 2 different cases here
        if (!isNew) {
            // Configuring a partially defined coverage
            builder.initCoverage(coverage, nativeCoverageName);
        } else {
            // Configuring a brand new coverage (only name has been specified)
            String specifiedName = coverage.getName();
            coverage = builder.buildCoverageByName(nativeCoverageName, specifiedName);
        }

        NamespaceInfo ns = coverage.getNamespace();
        if (ns != null && !ns.getPrefix().equals(workspace)) {
            //TODO: change this once the two can be different and we untie namespace
            // from workspace
            LOGGER.warning("Namespace: " + ns.getPrefix() + " does not match workspace: " + workspace + ", overriding.");
            ns = null;
        }

        if (ns == null) {
            //infer from workspace
            ns = catalog.getNamespaceByPrefix(workspace);
            coverage.setNamespace(ns);
        }

        coverage.setEnabled(true);
        catalog.validate(coverage, true).throwIfInvalid();
        catalog.add(coverage);

        //create a layer for the coverage
        catalog.add(builder.buildLayer(coverage));

        LOGGER.info("POST coverage " + coverageStoreName + "," + coverage.getName());
        return coverage.getName();
    }

    /**
     * This method returns {@code true} in case we have POSTed a Coverage object with the name only, as an instance
     * when configuring a new coverage which has just been harvested.
     *
     * @param coverage
     */
    private boolean isNewCoverage(CoverageInfo coverage) {
        return coverage.getName() != null && (coverage.isAdvertised()) && (!coverage.isEnabled())
                && (coverage.getAlias() == null) && (coverage.getCRS() == null)
                && (coverage.getDefaultInterpolationMethod() == null)
                && (coverage.getDescription() == null) && (coverage.getDimensions() == null)
                && (coverage.getGrid() == null) && (coverage.getInterpolationMethods() == null)
                && (coverage.getKeywords() == null) && (coverage.getLatLonBoundingBox() == null)
                && (coverage.getMetadata() == null) && (coverage.getNativeBoundingBox() == null)
                && (coverage.getNativeCRS() == null) && (coverage.getNativeFormat() == null)
                && (coverage.getProjectionPolicy() == null) && (coverage.getSRS() == null)
                && (coverage.getResponseSRS() == null) && (coverage.getRequestSRS() == null);
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return CoverageInfo.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public void configurePersister(XStreamPersister persister, XStreamMessageConverter converter) {
        persister.setCallback(new XStreamPersister.Callback() {
            @Override
            protected Class<CoverageInfo> getObjectClass() {
                return CoverageInfo.class;
            }

            @Override
            protected CatalogInfo getCatalogObject() {
                Map<String, String> uriTemplateVars = (Map<String, String>) RequestContextHolder.getRequestAttributes().getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
                String workspace = uriTemplateVars.get("workspace");
                String coveragestore = uriTemplateVars.get("store");
                String coverage = uriTemplateVars.get("coverage");

                if (workspace == null || coveragestore == null || coverage == null) {
                    return null;
                }
                CoverageStoreInfo cs = catalog.getCoverageStoreByName(workspace, coveragestore);
                if (cs == null) {
                    return null;
                }
                return catalog.getCoverageByCoverageStore(cs, coverage);
            }

            @Override
            protected void postEncodeReference(Object obj, String ref, String prefix,
                                               HierarchicalStreamWriter writer, MarshallingContext context) {
                if (obj instanceof NamespaceInfo) {
                    NamespaceInfo ns = (NamespaceInfo) obj;
                    converter.encodeLink("/namespaces/" + converter.encode(ns.getPrefix()), writer);
                }
                if (obj instanceof CoverageStoreInfo) {
                    CoverageStoreInfo cs = (CoverageStoreInfo) obj;
                    converter.encodeLink("/workspaces/" + converter.encode(cs.getWorkspace().getName()) +
                            "/coveragestores/" + converter.encode(cs.getName()), writer);

                }
            }
        });
    }
}
