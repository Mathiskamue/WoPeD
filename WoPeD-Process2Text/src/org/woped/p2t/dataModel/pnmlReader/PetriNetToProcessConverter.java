package org.woped.p2t.dataModel.pnmlReader;

import org.woped.p2t.dataModel.pnmlReader.PetriNet.PetriNet;
import dataModel.process.*;
import org.woped.p2t.dataModel.process.Arc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PetriNetToProcessConverter {
    private HashMap<String, Integer> transformedElems;
    public HashMap<Integer, String> transformedElemsRev;

    private String loopSet[] = new String[100];
    private int xor_split;
    private int xor_join;
    private int and_join;
    private int and_split;
    private int transitions;
    private int places;
    String types[] = new String[100];

    public org.woped.p2t.dataModel.process.ProcessModel convertToProcess(PetriNet petriNet) {

        org.woped.p2t.dataModel.process.ProcessModel model = new org.woped.p2t.dataModel.process.ProcessModel();
        org.woped.p2t.dataModel.process.Pool pool = new org.woped.p2t.dataModel.process.Pool("");
        org.woped.p2t.dataModel.process.Lane lane = new org.woped.p2t.dataModel.process.Lane("", pool.toString());
        model.addPool("");
        model.addLane("");

        transformedElems = new HashMap<>();
        transformedElemsRev = new HashMap<>();

        org.woped.p2t.dataModel.pnmlReader.PetriNet.Element startElem = petriNet.getElements().get(petriNet.getStartPlace());
        transformElem(startElem, -1, petriNet, model, pool, lane);
        return model;
    }


    private int x = 0;
    private static String xorTitle;
    //private static String andTitle;

    private void transformElem(org.woped.p2t.dataModel.pnmlReader.PetriNet.Element elem, int precElem, PetriNet petriNet, org.woped.p2t.dataModel.process.ProcessModel model, org.woped.p2t.dataModel.process.Pool pool, org.woped.p2t.dataModel.process.Lane lane) {
        // Id of current petri net element
        String elemId = elem.getId();
        String elemType = "";
        // If element not already excists
        if (!transformedElems.keySet().contains(elemId)) {

            // Places ...
            if (elem.getClass().toString().endsWith("Place")) {
                places++;

                // Simple place with 1 or more incoming and no outgoing arc
                if (petriNet.getSuccessor(elemId).size() == 0 && petriNet.getPredecessor(elemId).size() == 1) {
                    loopSet[x] = elemId + ": simple place, no ougoing arc";
                    x++;
                    int newId = model.getNewId();
                    model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId, "", lane, pool, org.woped.p2t.dataModel.process.GatewayType.XOR));
                    transformedElems.put(elemId, newId);
                    transformedElemsRev.put(newId, elemId);
                    if (precElem != -1) {
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                    }
                }

                // Simple place with 1 incoming and one outgoing arc
                if (petriNet.getSuccessor(elemId).size() == 1 && petriNet.getPredecessor(elemId).size() <= 1) {
                    loopSet[x] = elemId + ": simple place, one incoming, one outgoing arc";
                    x++;
                    String suc = petriNet.getSuccessor(elemId).get(0);
                    transformElem(petriNet.getElements().get(suc), precElem, petriNet, model, pool, lane);
                }

                //  Place with multiple outgoing arcs (XOR-Join)
                if (petriNet.getSuccessor(elemId).size() >= 0 && petriNet.getPredecessor(elemId).size() > 1) {
                    xor_join++;
                    loopSet[x] = elemId + ": XOR Join";
                    x++;
                    // Create new element
                    int newId = model.getNewId();
                    model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId, "", lane, pool, org.woped.p2t.dataModel.process.GatewayType.XOR));
                    transformedElems.put(elemId, newId);
                    transformedElemsRev.put(newId, elemId);
                    if (precElem != -1) {
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                    }

                    //if it is 0, there is no successor ...
                    if (!(petriNet.getSuccessor(elemId).size() == 0)) {
                        // Recursively go through the model
                        String suc = petriNet.getSuccessor(elemId).get(0);
                        transformElem(petriNet.getElements().get(suc), newId, petriNet, model, pool, lane);
                    } else {
                        //PetriNet ends with a XOR-Join
                        int newId3 = model.getNewId();
                        model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId3, "", lane, pool, org.woped.p2t.dataModel.process.GatewayType.XOR));
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(newId), model.getElem(newId3)));

                    }
                }

                // Place with multiple incoming arcs (XOR-Split)
                if (petriNet.getSuccessor(elemId).size() > 1 && petriNet.getPredecessor(elemId).size() >= 0) {
                    xor_split++;
                    loopSet[x] = elemId + ": XOR Split";
                    x++;
                    //Check XOR-Type
                    boolean checkXOR = false;
                    ArrayList<String> pr = petriNet.getSuccessor(elemId);
                    for (String aPr : pr) {
                        if (aPr.contains("op")) {
                            checkXOR = true;
                        }
                    }
                    if (checkXOR) {
                        //WoPeD XOR!! --> WoPeD specific operator
                        //Get Label from Successor --> XOR Label
                        String sucId = pr.get(0);
                        HashMap<String, org.woped.p2t.dataModel.pnmlReader.PetriNet.Element> elems = petriNet.getElements();
                        org.woped.p2t.dataModel.pnmlReader.PetriNet.Element sucXOR = elems.get(sucId);
                        String label = sucXOR.getLabel();
                        xorTitle = label;
                        //Add Activity
                        int newId = model.getNewId();
                        model.addActivity(new org.woped.p2t.dataModel.process.Activity(newId, label, null, null, ActivityType.NONE));
                        if (precElem != -1) {
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                        }
                        // Add Gateway
                        int newId2 = model.getNewId();
                        model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId2, "", lane, pool, org.woped.p2t.dataModel.process.GatewayType.XOR));
                        transformedElems.put(elemId, newId2);
                        transformedElemsRev.put(newId2, elemId);
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(newId), model.getElem(newId2)));

                        // Recursively go through the model
                        for (String suc : petriNet.getSuccessor(elemId)) {
                            transformElem(petriNet.getElements().get(suc), newId2, petriNet, model, pool, lane);
                        }

                    } else {
                        //Normal XOR --> without WoPeD XOR
                        // Create new element
                        int newId = model.getNewId();
                        model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId, "", lane, pool, org.woped.p2t.dataModel.process.GatewayType.XOR));
                        transformedElems.put(elemId, newId);
                        transformedElemsRev.put(newId, elemId);
                        if (precElem != -1) {
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                        }
                        // Recursively go through the model
                        for (String suc : petriNet.getSuccessor(elemId)) {
                            transformElem(petriNet.getElements().get(suc), newId, petriNet, model, pool, lane);
                        }
                    }


                }

                // Transitions...
            } else {
                transitions++;
                // Simple transition with 1 incoming and one outgoing arc
                if (petriNet.getSuccessor(elemId).size() == 1 && petriNet.getPredecessor(elemId).size() == 1) {
                    loopSet[x] = elemId + ": simple transition, one incoming, one outgoing arc, Type: " + elem.getType() + " ,role: " + elem.getRole();
                    x++;

                    //Role in Text --> Role in Petrinet = Lane in BPMN
                    org.woped.p2t.dataModel.process.Pool groupAsPool = new org.woped.p2t.dataModel.process.Pool(elem.getGroup());
                    org.woped.p2t.dataModel.process.Lane roleAsLane = new org.woped.p2t.dataModel.process.Lane(elem.getRole(), elem.getGroup());

                    if (elem.getRole().equals("none")) {
                        groupAsPool = null;
                        roleAsLane = null;
                    }

                    if (elem.getType() != null && elem.getType().equals("104")) {
                        //XOR-Split Transition
                        //WoPeD Transition, created by splitting specific XOR-Operator in several transitions

                        //Get Label from Successor --> Place Label
                        ArrayList<String> pr = petriNet.getSuccessor(elemId);
                        String sucId = pr.get(0);
                        HashMap<String, org.woped.p2t.dataModel.pnmlReader.PetriNet.Element> elems = petriNet.getElements();
                        org.woped.p2t.dataModel.pnmlReader.PetriNet.Element sucXOR = elems.get(sucId);
                        String label2 = sucXOR.getLabel();

                        int newId = model.getNewId();
                        model.addActivity(new org.woped.p2t.dataModel.process.Activity(newId, xorTitle + " " + label2, roleAsLane, groupAsPool, ActivityType.NONE));
                        transformedElems.put(elemId, newId);
                        transformedElemsRev.put(newId, elemId);
                        if (precElem != -1) {
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                        }

                        // Recursively go through the model
                        for (String suc : petriNet.getSuccessor(elemId)) {
                            transformElem(petriNet.getElements().get(suc), newId, petriNet, model, pool, lane);
                        }
                    }else if(elem.getType() != null && elem.getType().equals("105")) {
                        //WoPeD XOR!! --> WoPeD specific operator
                        //Get Label from Successor --> XOR Label

                        int newActivityId = model.getNewId();
                        model.addActivity(new org.woped.p2t.dataModel.process.Activity(newActivityId, "finish branch", null, null, ActivityType.NONE));
                        transformedElems.put(elemId, newActivityId);
                        transformedElemsRev.put(newActivityId, elemId);
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newActivityId)));


                        int XORId = getXORJoinID(elemId, model);
                        if (XORId == 0) {
                            // Gateway erstellen + mit vorheriger Activity
                            int indexOfChar = elemId.indexOf("_");
                            String start = elemId.substring(0, indexOfChar);
                            String XorId = start + "_XOR";


                            int newXorId = model.getNewId();
                            model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newXorId, XorId, lane, pool, org.woped.p2t.dataModel.process.GatewayType.XOR));
                            transformedElems.put(elemId, newXorId);
                            transformedElemsRev.put(newXorId, elemId);
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(newActivityId), model.getElem(newXorId)));

                            // Activtiy erstellen welches das XOR-Join label darstellt und das mit Gateway verbinden
                            newActivityId = model.getNewId();
                            model.addActivity(new org.woped.p2t.dataModel.process.Activity(newActivityId, elem.getLabel(), null, null, ActivityType.NONE));
                            transformedElems.put(elemId, newActivityId);
                            transformedElemsRev.put(newActivityId, elemId);
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(newXorId), model.getElem(newActivityId)));

                        } else {
                            //vorheriges Element mit dem GatewayID verbinden
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(newActivityId), model.getElem(XORId)));
                        }


                        String activityLabel = elem.getLabel();
                        HashMap<Integer, org.woped.p2t.dataModel.process.Activity> activityHashMap = model.getActivites();
                        Iterator it = activityHashMap.entrySet().iterator();

                        int activityId = 0;
                        while (it.hasNext()) {
                            Map.Entry element = (Map.Entry) it.next();
                            org.woped.p2t.dataModel.process.Activity g = (org.woped.p2t.dataModel.process.Activity) element.getValue();
                            String label = g.getLabel();
                            if (activityLabel.equals(label)) {
                                activityId = g.getId();
                            }
                        }

                        //Recursively go through the model
                        for (String suc : petriNet.getSuccessor(elemId)) {
                            transformElem(petriNet.getElements().get(suc), activityId, petriNet, model, pool, lane);
                        }


                    }else {
                        //Normal Transition
                        int newId = model.getNewId();
                        String label = elem.getLabel();
                        int type = elem.getId().contains("sub") ? ActivityType.TYPE_MAP.get("Subprocess") : ActivityType.NONE;
                        model.addActivity(new org.woped.p2t.dataModel.process.Activity(newId, label, roleAsLane, groupAsPool, type));
                        transformedElems.put(elemId, newId);
                        transformedElemsRev.put(newId, elemId);
                        if (precElem != -1) {
                            model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                        }

                        // Recursively go through the model
                        for (String suc : petriNet.getSuccessor(elemId)) {
                            transformElem(petriNet.getElements().get(suc), newId, petriNet, model, pool, lane);
                        }
                    }
                }

                //  Transition with multiple incoming arcs (AND-Join)
                if (petriNet.getSuccessor(elemId).size() == 1 && petriNet.getPredecessor(elemId).size() > 1) {
                    loopSet[x] = elemId + ": AND Join";
                    and_join++;
                    // Create new element
                    int newId = model.getNewId();
                    model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId, "", lane, pool, org.woped.p2t.dataModel.process.GatewayType.AND));
                    transformedElems.put(elemId, newId);
                    transformedElemsRev.put(newId, elemId);
                    if (precElem != -1) {
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newId)));
                    }

                    // Recursively go through the model
                    String suc = petriNet.getSuccessor(elemId).get(0);
                    transformElem(petriNet.getElements().get(suc), newId, petriNet, model, pool, lane);
                }

                //  Transition with multiple incoming arcs (AND-Split)
                if (petriNet.getSuccessor(elemId).size() > 1 && petriNet.getPredecessor(elemId).size() == 1) {
                    loopSet[x] = elemId + ": AND Split";
                    and_split++;

                    // Activitiy erstellen welches das And-Split Label darstellt und das mit Gateway verbinden
                    int newActivityId = model.getNewId();
                    String label = elem.getLabel();
                    model.addActivity(new org.woped.p2t.dataModel.process.Activity(newActivityId, label, null, null, ActivityType.NONE));
                    transformedElems.put(elemId + "_AndLabel", newActivityId);
                    transformedElemsRev.put(newActivityId, elemId + "_AndLabel");
                    if (precElem != -1) {
                        model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(newActivityId)));
                    }

                    // Create new element
                    int newId = model.getNewId();
                    model.addGateway(new org.woped.p2t.dataModel.process.Gateway(newId, label, lane, pool, org.woped.p2t.dataModel.process.GatewayType.AND));
                    transformedElems.put(elemId, newId);
                    transformedElemsRev.put(newId, elemId);
                    model.addArc(new org.woped.p2t.dataModel.process.Arc(model.getNewId(), "", model.getElem(newActivityId), model.getElem(newId)));


                    // Recursively go through the model
                    for (String suc : petriNet.getSuccessor(elemId)) {
                        transformElem(petriNet.getElements().get(suc), newId, petriNet, model, pool, lane);
                    }
                }
            }
        } else {
            model.addArc(new Arc(model.getNewId(), "", model.getElem(precElem), model.getElem(transformedElems.get(elem.getId()))));
        }

    }

    private int getXORJoinID(String elementId, org.woped.p2t.dataModel.process.ProcessModel model){
        int indexOfChar=elementId.indexOf("_");
        String start =elementId.substring(0,indexOfChar);

        String startXor = start + "_XOR";

        HashMap<Integer, org.woped.p2t.dataModel.process.Gateway> gatewayHashMap = model.getGateways();

        Iterator it = gatewayHashMap.entrySet().iterator();

        int id = 0;
        while(it.hasNext()){
            Map.Entry element = (Map.Entry)it.next();
            org.woped.p2t.dataModel.process.Gateway g = (org.woped.p2t.dataModel.process.Gateway) element.getValue();
            String label = g.getLabel();
            if(label.equals(startXor)){
                id = g.getId();
            }
        }

        return id;
    }

    public void printConversion() {
        for (int i = loopSet.length; i > 0; i--) {
            if (loopSet[i - 1] != null) {
                System.out.println(i + ". Element: " + loopSet[i - 1]);
            }
        }
        System.out.println("Places: " + places);
        System.out.println("Transition " + transitions);
        System.out.println("XOR-Splits: " + xor_split);
        System.out.println("XOR-Joins: " + xor_join);
        System.out.println("AND-Splits: " + and_split);
        System.out.println("AND-Joins: " + and_join);

    }
}